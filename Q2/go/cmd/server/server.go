/*
   Data de criação: 11/10/2026
   Estudante: Gustavo Martins França
   Definição do servidor UDP
*/

package main

import (
	"bytes"
	"crypto/sha1"
	"hash"
	"log"
	"net"
	"os"
	"path/filepath"
	"time"

	"sockets_udp/Q2/internal"
)

const idleTimeout = 5 * time.Second

type session struct {
	client       *net.UDPAddr
	tmpPath      string
	finalPath    string
	file         *os.File
	hash         hash.Hash
	declaredSize uint64
	totalPackets uint64
	received     uint64
	expectedSeq  internal.SeqType
	lastActive   time.Time
}

// Remembers the outcome of the last transfer so a lost Response can be re-sent
type lastResult struct {
	client string
	hash   [sha1.Size]byte
	status internal.Status
}

type Server struct {
	addr     *net.UDPAddr
	conn     *net.UDPConn
	baseDir  string
	protocol *internal.Protocol
	// only one transfer at a time
	sess *session
	last *lastResult
}

func NewServer(addr *net.UDPAddr, baseDir string) *Server {
	return &Server{
		addr:    addr,
		baseDir: baseDir,
	}
}

// Starts the server and forwards messages to handleMessage()
func (s *Server) Run() {
	var err error

	s.conn, err = net.ListenUDP("udp", s.addr)

	if err != nil {
		log.Fatalf("Error listening on %s: %v", s.addr, err)
	}

	defer s.conn.Close()

	s.protocol = internal.NewProtocol(s.conn, nil)
	log.Printf("UDP Server listening on %s", s.addr)

	for {
		s.conn.SetReadDeadline(time.Now().Add(time.Second))
		msg, addr, err := s.protocol.ReadMessage()

		if s.sess != nil && time.Since(s.sess.lastActive) > idleTimeout {
			log.Printf("Timeout for client %s. Aborting transfer.", s.sess.client)
			s.resetSession(true)
		}

		if err != nil {
			if netErr, ok := err.(net.Error); !ok || !netErr.Timeout() {
				log.Printf("Error reading message: %v", err)
			}

			continue
		}

		s.handleMessage(msg, addr)
	}
}

func (s *Server) handleMessage(msg *internal.Message, addr *net.UDPAddr) {
	if s.sess != nil {
		if s.sess.client.String() != addr.String() {
			log.Printf("Ignored packet from %s (busy with %s)", addr, s.sess.client)
			return
		}

		s.sess.lastActive = time.Now()
	}

	switch msg.MessageType {
	case internal.MessageRequest:
		s.handleRequest(msg, addr)

	case internal.MessageHeader:
		s.handleHeader(msg, addr)

	case internal.MessageData:
		s.handleData(msg, addr)

	case internal.MessageChecksum:
		s.handleChecksum(msg, addr)

	}
}

func (s *Server) handleRequest(msg *internal.Message, addr *net.UDPAddr) {
	if msg.Command != internal.CmdAddFile {
		return
	}

	if s.sess == nil {
		log.Printf("New ADDFILE request from %s", addr)
		s.sess = &session{client: addr, lastActive: time.Now()}
	}

	// also covers retransmitted/delayed requests: just ACK again
	s.sendACK(addr, internal.SeqRequest)
}

func (s *Server) handleHeader(msg *internal.Message, addr *net.UDPAddr) {
	sess := s.sess
	if sess == nil {
		return
	}

	// retransmitted header: our ACK was lost
	if sess.file != nil {
		s.sendACK(addr, internal.SeqHeader)
		return
	}

	finalPath, ok := s.resolvePath(msg.FileName)
	if !ok {
		log.Printf("Invalid file name in header from %s", addr)
		s.resetSession(true)
		return
	}

	tmpPath := finalPath + ".part"
	f, err := os.Create(tmpPath)
	if err != nil {
		log.Printf("Error creating %s: %v", tmpPath, err)
		s.resetSession(true)
		return
	}

	sess.finalPath = finalPath
	sess.tmpPath = tmpPath
	sess.file = f
	sess.hash = sha1.New()
	sess.declaredSize = msg.FileSize
	sess.totalPackets = (msg.FileSize + internal.MaxPayloadSize - 1) / internal.MaxPayloadSize
	sess.expectedSeq = internal.SeqFirstData

	log.Printf("Header received. Expecting %s (%d bytes, %d packets)", msg.FileName, msg.FileSize, sess.totalPackets)
	s.sendACK(addr, internal.SeqHeader)
}

func (s *Server) handleData(msg *internal.Message, addr *net.UDPAddr) {
	sess := s.sess

	if sess == nil || sess.file == nil {
		return // no header yet
	}

	switch {
	case msg.Seq == sess.expectedSeq:
		if sess.received+uint64(len(msg.Payload)) > sess.declaredSize {
			log.Printf("Client %s sent more data than declared", addr)
			s.resetSession(true)
			return
		}

		if _, err := sess.file.Write(msg.Payload); err != nil {
			log.Printf("Write error for %s: %v", addr, err)
			s.resetSession(true)
			return
		}

		sess.hash.Write(msg.Payload)
		sess.received += uint64(len(msg.Payload))
		sess.expectedSeq++
		s.sendACK(addr, msg.Seq)

	case msg.Seq < sess.expectedSeq:
		s.sendACK(addr, msg.Seq)
	}
}

func (s *Server) handleChecksum(msg *internal.Message, addr *net.UDPAddr) {
	sess := s.sess

	if sess == nil {
		// session already finished; retransmitting because our Response was lost
		if l := s.last; l != nil && l.client == addr.String() && l.hash == msg.Hash {
			s.sendResponse(addr, l.status)
		}

		return
	}

	if sess.file == nil {
		return // no header yet
	}

	closeErr := sess.file.Close()
	sess.file = nil

	ok := closeErr == nil &&
		sess.received == sess.declaredSize &&
		bytes.Equal(sess.hash.Sum(nil), msg.Hash[:])

	if ok {
		ok = os.Rename(sess.tmpPath, sess.finalPath) == nil
	}

	status := internal.StatusError

	if ok {
		status = internal.StatusSuccess
		log.Printf("File '%s' received successfully from %s", sess.finalPath, addr)
	} else {
		log.Printf("Transfer from %s failed validation", addr)
	}

	s.last = &lastResult{client: addr.String(), hash: msg.Hash, status: status}
	s.resetSession(!ok)
	s.sendResponse(addr, status)
}

func (s *Server) sendACK(addr *net.UDPAddr, seq internal.SeqType) {
	ack := &internal.Message{
		MessageType: internal.MessageACK,
		Seq:         seq,
	}

	if err := s.protocol.WriteMessage(ack, addr); err != nil {
		log.Printf("Error sending ACK to %s: %v", addr, err)
	}
}

func (s *Server) sendResponse(addr *net.UDPAddr, status internal.Status) {
	resp := &internal.Message{
		MessageType: internal.MessageResponse,
		StatusCode:  status,
	}

	if err := s.protocol.WriteMessage(resp, addr); err != nil {
		log.Printf("Error sending response to %s: %v", addr, err)
	}
}

// Ends the current session; discard = true also removes the partial file
func (s *Server) resetSession(discard bool) {
	if sess := s.sess; sess != nil {
		if sess.file != nil {
			sess.file.Close()
		}
		if discard && sess.tmpPath != "" {
			os.Remove(sess.tmpPath)
		}
	}
	s.sess = nil
}

func (s *Server) resolvePath(name string) (string, bool) {
	if name == "" || name == "." || name == ".." || filepath.Base(name) != name {
		return "", false
	}

	return filepath.Join(s.baseDir, name), true
}
