/*
   Data de criação: 11/10/2026
   Estudante: Gustavo Martins França
   Definição da mensagem do protocolo
*/

package internal

type CommandType byte
type MessageType byte
type SeqType uint32
type Status uint8

const (
	MessageRequest  MessageType = 0x01
	MessageResponse MessageType = 0x02
	MessageHeader   MessageType = 0x03
	MessageData     MessageType = 0x04
	MessageChecksum MessageType = 0x05
	MessageACK      MessageType = 0x06
)

// might need to change request and header to be different
const (
	SeqRequest   SeqType = 0
	SeqHeader    SeqType = 0
	SeqFirstData SeqType = 1
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
	HashSize        = 20 // SHA-1
)

type Message struct {
	MessageType MessageType
	// Request
	Command CommandType
	// Response
	StatusCode Status
	// Header
	FileSize uint64
	FileName string
	//Data
	Seq     SeqType // reused for ACK
	Payload []byte
	// Checksum
	Hash [HashSize]byte
}
