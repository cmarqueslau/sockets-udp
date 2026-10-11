/*
   Data de criação: 11/10/2026
   Estudante: Gustavo Martins França
   Definição da mensagem do protocolo
*/

package internal

type CommandType byte
type MessageType byte
type Status uint8

const (
	MessageRequest  MessageType = 0x01
	MessageResponse MessageType = 0x02
	MessageHeader   MessageType = 0x03
	MessageData     MessageType = 0x04
	MessageChecksum MessageType = 0x05
	MessageACK      MessageType = 0x06
)

const (
	CmdAddFile CommandType = 0x01
)

const (
	StatusSuccess Status = 0x01
	StatusError   Status = 0x02
)

const (
	MaxFileNameSize = 255
	MaxPayloadSize  = 1024
)

type Message struct {
	MessageType MessageType
	// Request
	Command CommandType
	// Response
	StatusCode Status
	// Header
	NameSize uint8
	FileSize uint64
	FileName string
	//Data
	Seq         uint32 // reused for ACK
	PayloadSize uint16
	Payload     []byte
	// Checksum
	Hash [20]byte
}
