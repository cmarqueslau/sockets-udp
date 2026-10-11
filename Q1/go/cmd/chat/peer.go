/*
   Data de criação: 10/10/2026
   Estudante: Gustavo Martins França
   Definição de um peer no contexto de um chat P2P
*/

package main

import (
	"fmt"
	"net"

	protocol "sockets_udp/Q1/internal"
)

type Peer struct {
	nickname string
	conn     *net.UDPConn
	proto    *protocol.Protocol
}

// Binds the local port and destination
func NewPeer(localPort int, remoteIP string, remotePort int, nickname string) (*Peer, error) {
	source := &net.UDPAddr{Port: localPort}
	conn, err := net.ListenUDP("udp", source)

	if err != nil {
		return nil, fmt.Errorf("Error listening to scoket: %w", err)
	}

	dest := &net.UDPAddr{
		IP:   net.ParseIP(remoteIP),
		Port: remotePort,
	}

	peer := &Peer{
		nickname: nickname,
		conn:     conn,
		proto:    protocol.NewProtocol(conn, dest),
	}

	return peer, nil
}

// Handles incoming messages
func (p *Peer) Receive() {
	for {
		msg, source, err := p.proto.ReadMessage()

		if err != nil {
			fmt.Printf("Error receiving message: %v\n", err)
			break
		}

		if msg.MessageType == protocol.MessageECHO {
			msg.MessageType = protocol.MessageNormal

			p.proto.WriteMessage(msg, source)
		}

		switch msg.MessageType {
		case protocol.MessageEmoji:
			switch msg.MessageData {
			case "1":
				msg.MessageData = ":)"

			case "2":
				msg.MessageData = ":("

			case "3":
				msg.MessageData = ":)"
			}

		case protocol.MessageURL:
			msg.MessageData = fmt.Sprintf("\033[4m%s\033[0m", msg.MessageData) // \033[4m and \033[0m" to underline text when it's an URL
		}

		fmt.Printf("%s: %s\n", msg.Nick, msg.MessageData)
	}
}

// Formats the message and sends it
func (p *Peer) Send(msgType protocol.MessageType, msgData string) error {
	msg := &protocol.Message{
		MessageType: msgType,
		NickSize:    uint8(len(p.nickname)),
		Nick:        p.nickname,
		MessageSize: uint8(len(msgData)),
		MessageData: msgData,
	}

	return p.proto.WriteMessage(msg, nil)
}

// Closes the socket
func (p *Peer) Close() {
	if p.conn != nil {
		p.conn.Close()
	}
}
