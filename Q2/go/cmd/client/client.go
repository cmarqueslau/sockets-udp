/*
   Data de criação: 11/10/2026
   Estudante: Gustavo Martins França
   Definição do cliente UDP
*/

package main

import (
	"bufio"
	"crypto/sha1"
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"path/filepath"
	"strings"
	"time"

	"sockets_udp/Q2/internal"
)

const (
	MaxRetries = 5
	Timeout    = 200 * time.Millisecond
)

type Client struct {
	serverAddr  *net.UDPAddr
	conn        *net.UDPConn
	proto       *internal.Protocol
	inputReader *bufio.Reader
}

func NewClient(serverAddr *net.UDPAddr) *Client {
	return &Client{serverAddr: serverAddr}
}

// Connects to the server and handles the input commands
func (c *Client) Run() {
	var err error

	c.conn, err = net.DialUDP("udp", nil, c.serverAddr)

	if err != nil {
		log.Fatalf("Error connecting to server: %v", err)
	}

	defer c.conn.Close()

	c.proto = internal.NewProtocol(c.conn, nil)
	c.inputReader = bufio.NewReader(os.Stdin)

	fmt.Printf("Connected to UDP Server at %s\n", c.serverAddr)

	for {
		fmt.Print(">> ")

		str, err := c.inputReader.ReadString('\n')

		if err != nil {
			if err == io.EOF {
				fmt.Println("\nDisconnected")
			} else {
				fmt.Printf("Error reading input: %v\n", err)
			}

			return
		}

		tokens := strings.Fields(str)

		if len(tokens) == 0 {
			continue
		}

		command := strings.ToUpper(tokens[0])

		switch command {
		case "EXIT":
			fmt.Println("Disconnected")
			return

		case "ADDFILE":
			if len(tokens) < 2 {
				fmt.Println("Usage: ADDFILE <local_path>")
				continue
			}

			c.addFile(tokens[1])

		default:
			fmt.Printf("Unknown command: %s\n", command)
		}
	}
}

// handles ADDFILE
func (c *Client) addFile(localPath string) {
	if err := c.uploadFile(localPath); err != nil {
		fmt.Println(err)
		return
	}

	fmt.Println("File uploaded and verified successfully!")
}

func (c *Client) uploadFile(localPath string) error {
	file, err := os.Open(localPath)

	if err != nil {
		return fmt.Errorf("error opening local file: %w", err)
	}

	defer file.Close()

	info, err := file.Stat()

	if err != nil {
		return fmt.Errorf("error getting file info: %w", err)
	}

	fileName := filepath.Base(localPath)

	if len(fileName) > internal.MaxFileNameSize {
		return fmt.Errorf("file name too long (max = %d)", internal.MaxFileNameSize)
	}

	if err := c.sendRequest(internal.CmdAddFile); err != nil {
		return fmt.Errorf("request failed: %w", err)
	}

	if err := c.sendHeader(fileName, uint64(info.Size())); err != nil {
		return fmt.Errorf("header transfer failed: %w", err)
	}

	sum, err := c.sendData(file)

	if err != nil {
		return err
	}

	return c.sendChecksum(sum)
}

func (c *Client) sendRequest(cmd internal.CommandType) error {
	msg := &internal.Message{
		MessageType: internal.MessageRequest,
		Command:     cmd,
	}

	return c.sendAcked(msg, internal.SeqRequest)
}

func (c *Client) sendHeader(name string, size uint64) error {
	msg := &internal.Message{
		MessageType: internal.MessageHeader,
		FileSize:    size,
		FileName:    name,
	}

	return c.sendAcked(msg, internal.SeqHeader)
}

func (c *Client) sendData(r io.Reader) ([sha1.Size]byte, error) {
	var sum [sha1.Size]byte
	hash := sha1.New()
	buffer := make([]byte, internal.MaxPayloadSize)

	for seq := internal.SeqFirstData; ; seq++ {
		n, err := r.Read(buffer)

		if n > 0 {
			hash.Write(buffer[:n])

			msg := &internal.Message{
				MessageType: internal.MessageData,
				Seq:         seq,
				Payload:     buffer[:n],
			}

			if err := c.sendAcked(msg, seq); err != nil {
				return sum, fmt.Errorf("data transfer failed at sequence %d: %w", seq, err)
			}
		}

		if err == io.EOF {
			copy(sum[:], hash.Sum(nil))
			return sum, nil
		}

		if err != nil {
			return sum, fmt.Errorf("error reading file: %w", err)
		}
	}
}

func (c *Client) sendChecksum(sum [sha1.Size]byte) error {
	msg := &internal.Message{
		MessageType: internal.MessageChecksum,
		Hash:        sum,
	}

	resp, err := c.sendMessage(msg, internal.MessageResponse, 0)

	if err != nil {
		return fmt.Errorf("checksum transfer failed: %w", err)
	}

	if resp.StatusCode != internal.StatusSuccess {
		return errors.New("checksum validation failed on the server")
	}

	return nil
}

func (c *Client) sendAcked(msg *internal.Message, seq internal.SeqType) error {
	_, err := c.sendMessage(msg, internal.MessageACK, seq)
	return err
}

// Sends a message and blocks until the expected ACK or Response is received, or retries run out
func (c *Client) sendMessage(msg *internal.Message, expectedType internal.MessageType, expectedSeq internal.SeqType) (*internal.Message, error) {
	for i := 1; i <= MaxRetries; i++ {
		if err := c.proto.WriteMessage(msg, nil); err != nil {
			return nil, fmt.Errorf("write error: %w", err)
		}

		c.conn.SetReadDeadline(time.Now().Add(Timeout))

		for {
			respMsg, _, err := c.proto.ReadMessage()

			if err != nil {
				netErr, isNetErr := err.(net.Error)

				if !isNetErr {
					continue // malformed packet
				}

				if netErr.Timeout() {
					break // timeout; retransmit
				}

				return nil, err
			}

			if respMsg.MessageType != expectedType {
				continue
			}

			// if seqs mismatchs between ACKs
			if expectedType == internal.MessageACK && respMsg.Seq != expectedSeq {
				continue
			}

			return respMsg, nil
		}
	}

	return nil, fmt.Errorf("max retries (%d) reached", MaxRetries)
}
