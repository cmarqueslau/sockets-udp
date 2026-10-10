package protocol

import (
	"fmt"
	"net"
)

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

// ReadMessage reads a UDP datagram and returns the Message and the sender's address
func (p *Protocol) ReadMessage() (*Message, *net.UDPAddr, error) {
	packageBuffer := make([]byte, MaxPackageSize)

	n, sender, err := p.conn.ReadFromUDP(packageBuffer)

	if err != nil {
		return nil, nil, err
	}

	// The package needs at least messsgeType, nickSize, nick, messageSize,
	if n < 4 {
		return nil, nil, fmt.Errorf("Package too small to contain all fields")
	}

	messageType := MessageType(packageBuffer[0])
	nickSize := packageBuffer[1]

	if nickSize < 1 || nickSize > 64 {
		return nil, nil, fmt.Errorf("Invalid nick size")
	}

	messageSizeStart := 2 + int(nickSize)
	nick := string(packageBuffer[2:messageSizeStart])
	messageSize := packageBuffer[messageSizeStart]
	messageStart := messageSizeStart + 1
	messageData := string(packageBuffer[messageStart : messageStart+int(messageSize)])

	msg := &Message{
		MessageType: messageType,
		NickSize:    nickSize,
		Nick:        nick,
		MessageSize: messageSize,
		MessageData: messageData,
	}

	return msg, sender, nil
}

// WriteMessage formats the Message to bytes and sends it to the destination
func (p *Protocol) WriteMessage(req *Message, dest *net.UDPAddr) error {
	if req.NickSize < 1 || req.NickSize > 64 {
		return fmt.Errorf("Nick must be between 1 and 64 bytes")
	}

	packageSize := 3 + int(req.NickSize) + int(req.MessageSize)
	packageBuffer := make([]byte, packageSize)

	packageBuffer[0] = byte(req.MessageType)
	packageBuffer[1] = req.NickSize
	copy(packageBuffer[2:], []byte(req.Nick))

	messageSizeIndex := 2 + int(req.NickSize)
	packageBuffer[messageSizeIndex] = req.MessageSize
	copy(packageBuffer[messageSizeIndex+1:], []byte(req.MessageData))

	_, err := p.conn.WriteToUDP(packageBuffer, dest)

	return err
}
