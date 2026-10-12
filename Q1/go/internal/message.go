/*
   Data de criação: 09/10/2026
   Estudante: Gustavo Martins França
   Definição da mensagem do protocolo
*/

package internal

type MessageType byte

const (
	MessageNormal MessageType = 0x01
	MessageEmoji  MessageType = 0x02
	MessageURL    MessageType = 0x03
	MessageECHO   MessageType = 0x04
)

const (
	MaxNickSize    = 64
	MaxMessageSize = 255
	MaxPackageSize = 1 + 1 + MaxNickSize + 1 + MaxMessageSize
)

type Message struct {
	MessageType MessageType
	NickSize    uint8 // 1-2^6 bytes
	Nick        string
	MessageSize uint8 // 1-2^8 bytes
	MessageData string
}
