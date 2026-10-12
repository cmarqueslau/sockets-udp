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

// biggest packet = Data
const MaxPackageSize = 1 + 4 + 2 + MaxPayloadSize

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

// Reads a datagram and decodes it into a Message, validating every length against what arrived
func (p *Protocol) ReadMessage() (*Message, *net.UDPAddr, error) {
	buffer := make([]byte, MaxPackageSize)

	n, source, err := p.conn.ReadFromUDP(buffer)

	if err != nil {
		return nil, nil, err
	}

	pkt := buffer[:n]

	if len(pkt) < 1 {
		return nil, source, fmt.Errorf("empty packet")
	}

	msg := &Message{
		MessageType: MessageType(pkt[0]),
	}

	switch msg.MessageType {
	case MessageRequest:
		if len(pkt) < 2 {
			return nil, source, fmt.Errorf("short request")
		}

		msg.Command = CommandType(pkt[1])

	case MessageResponse:
		if len(pkt) < 2 {
			return nil, source, fmt.Errorf("short response")
		}

		msg.StatusCode = Status(pkt[1])

	case MessageHeader:
		if len(pkt) < 10 || len(pkt) != 10+int(pkt[1]) {
			return nil, source, fmt.Errorf("header length mismatch")
		}

		msg.FileSize = binary.BigEndian.Uint64(pkt[2:10])
		msg.FileName = string(pkt[10:])

	case MessageData:
		if len(pkt) < 7 {
			return nil, source, fmt.Errorf("short data")
		}

		size := int(binary.BigEndian.Uint16(pkt[5:7]))

		if size > MaxPayloadSize || len(pkt) != 7+size {
			return nil, source, fmt.Errorf("data length mismatch")
		}

		msg.Seq = SeqType(binary.BigEndian.Uint32(pkt[1:5]))
		msg.Payload = pkt[7:]

	case MessageChecksum:
		if len(pkt) < 1+HashSize {
			return nil, source, fmt.Errorf("short checksum")
		}

		copy(msg.Hash[:], pkt[1:1+HashSize])

	case MessageACK:
		if len(pkt) < 5 {
			return nil, source, fmt.Errorf("short ack")
		}

		msg.Seq = SeqType(binary.BigEndian.Uint32(pkt[1:5]))

	default:
		return nil, source, fmt.Errorf("unknown message type %d", msg.MessageType)
	}

	return msg, source, nil
}

// Encodes a Message to the binary protocol and sends it
func (p *Protocol) WriteMessage(msg *Message, dest *net.UDPAddr) error {
	var buffer []byte

	switch msg.MessageType {
	case MessageRequest:
		buffer = []byte{byte(msg.MessageType), byte(msg.Command)}

	case MessageResponse:
		buffer = []byte{byte(msg.MessageType), byte(msg.StatusCode)}

	case MessageHeader:
		nameLen := len(msg.FileName)

		if nameLen == 0 || nameLen > MaxFileNameSize {
			return fmt.Errorf("invalid file name length: %d", nameLen)
		}

		buffer = make([]byte, 10+nameLen)
		buffer[0] = byte(msg.MessageType)
		buffer[1] = uint8(nameLen)
		binary.BigEndian.PutUint64(buffer[2:10], msg.FileSize)
		copy(buffer[10:], msg.FileName)

	case MessageData:
		size := len(msg.Payload)

		if size > MaxPayloadSize {
			return fmt.Errorf("payload too large: %d", size)
		}

		buffer = make([]byte, 7+size)
		buffer[0] = byte(msg.MessageType)
		binary.BigEndian.PutUint32(buffer[1:5], uint32(msg.Seq))
		binary.BigEndian.PutUint16(buffer[5:7], uint16(size))
		copy(buffer[7:], msg.Payload)

	case MessageChecksum:
		buffer = make([]byte, 1+HashSize)
		buffer[0] = byte(msg.MessageType)
		copy(buffer[1:], msg.Hash[:])

	case MessageACK:
		buffer = make([]byte, 5)
		buffer[0] = byte(msg.MessageType)
		binary.BigEndian.PutUint32(buffer[1:5], uint32(msg.Seq))

	default:
		return fmt.Errorf("unknown message type: %d", msg.MessageType)
	}

	var err error
	switch {
	case dest != nil:
		_, err = p.conn.WriteToUDP(buffer, dest)

	case p.dest != nil:
		_, err = p.conn.WriteToUDP(buffer, p.dest)

	default:
		_, err = p.conn.Write(buffer)
	}

	return err
}
