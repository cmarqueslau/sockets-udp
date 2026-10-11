/*
   Data de criação: 11/10/2026
   Estudante: Gustavo Martins França
   Definição do protocolo comum entre linguagens
*/

package internal

import (
	"encoding/binary"
	"fmt"
	"net"
)

// biggest package = Data
const MaxPackageSize = 1 + 4 + 2 + 1024

type Protocol struct {
	conn *net.UDPConn
	dest *net.UDPAddr
}

func NewProtocol(conn *net.UDPConn, dest *net.UDPAddr) *Protocol {
	return &Protocol{
		conn: conn,
		dest: dest,
	}
}

// Reads a protocol message and returnes a Message with the approriate fields
func (p *Protocol) ReadMessage() (*Message, *net.UDPAddr, error) {
	buffer := make([]byte, MaxPackageSize)

	n, source, err := p.conn.ReadFromUDP(buffer)

	if err != nil {
		return nil, nil, err
	}

	if n < 1 {
		return nil, nil, fmt.Errorf("Package is too small")
	}

	msg := &Message{
		MessageType: MessageType(buffer[0]),
	}

	switch msg.MessageType {
	case MessageRequest:
		if n >= 2 {
			msg.Command = CommandType(buffer[1])
		}

	case MessageResponse:
		if n >= 2 {
			msg.StatusCode = Status(buffer[1])
		}

	case MessageHeader:
		if n >= 10 {
			msg.NameSize = buffer[1]
			msg.FileSize = binary.BigEndian.Uint64(buffer[2:10])
			msg.FileName = string(buffer[10 : 10+int(msg.NameSize)])
		}

	case MessageData:
		if n >= 7 {
			msg.Seq = binary.BigEndian.Uint32(buffer[1:5])
			msg.PayloadSize = binary.BigEndian.Uint16(buffer[5:7])
			msg.Payload = buffer[7 : 7+int(msg.PayloadSize)]
		}

	case MessageChecksum:
		if n >= 21 {
			copy(msg.Hash[:], buffer[1:21])
		}

	case MessageACK:
		if n >= 5 {
			msg.Seq = binary.BigEndian.Uint32(buffer[1:5])
		}

	default:
		return nil, source, fmt.Errorf("Unknowb MessageType: %d", msg.MessageType)
	}

	return msg, source, nil
}

// Formats a Message to the binary protocol and sends it
func (p *Protocol) WriteMessage(msg *Message, dest *net.UDPAddr) error {
	var buffer []byte

	switch msg.MessageType {
	case MessageRequest:
		buffer = make([]byte, 2)
		buffer[0] = byte(msg.MessageType)
		buffer[1] = byte(msg.Command)

	case MessageResponse:
		buffer = make([]byte, 2)
		buffer[0] = byte(msg.MessageType)
		buffer[1] = byte(msg.StatusCode)

	case MessageHeader:
		buffer = make([]byte, 10+len(msg.FileName))
		buffer[0] = byte(msg.MessageType)
		buffer[1] = msg.NameSize
		binary.BigEndian.PutUint64(buffer[2:10], msg.FileSize)
		copy(buffer[10:], msg.FileName)

	case MessageData:
		buffer = make([]byte, 7+len(msg.Payload))
		buffer[0] = byte(msg.MessageType)
		binary.BigEndian.PutUint32(buffer[1:5], msg.Seq)
		binary.BigEndian.PutUint16(buffer[5:7], msg.PayloadSize)
		copy(buffer[7:], msg.Payload)

	case MessageChecksum:
		buffer = make([]byte, 21)
		buffer[0] = byte(msg.MessageType)
		copy(buffer[1:], msg.Hash[:])

	case MessageACK:
		buffer = make([]byte, 5)
		buffer[0] = byte(msg.MessageType)
		binary.BigEndian.PutUint32(buffer[1:5], msg.Seq)

	default:
		return fmt.Errorf("Unknown Message Type: %d", msg.MessageType)
	}

	target := dest

	if target == nil {
		target = p.dest
	}

	_, err := p.conn.WriteToUDP(buffer, target)
	return err
}
