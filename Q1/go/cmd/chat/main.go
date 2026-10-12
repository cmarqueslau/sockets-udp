/*
   Data de criação: 10/10/2026
   Estudante: Gustavo Martins França
   Definição de um chat 1-1 P2P
*/

package main

import (
	"bufio"
	"fmt"
	"os"
	"strconv"
	"strings"

	protocol "sockets_udp/Q1/internal"
)

type Chat struct {
	peer *Peer
}

func NewChat(peer *Peer) *Chat {
	return &Chat{
		peer: peer,
	}
}

// Starts the peer and creates goroutines to handle receiving
func (c *Chat) Start() {
	fmt.Println("Write a message and it's type (e.g. '1 hiiii')")
	fmt.Println("Allowed message types: 1 - Normal | 2 - Emoji | 3 - URL | 4 - ECHO")
	fmt.Println("Type EXIT to exit application\n")

	go c.peer.Receive()

	scanner := bufio.NewScanner(os.Stdin)

	for scanner.Scan() {
		input := scanner.Text()

		if strings.ToUpper(input) == "EXIT" {
			break
		}

		before, after, ok := strings.Cut(input, " ")

		if !ok {
			fmt.Println("Usage: <message_type> <message>")
			continue
		}

		msgTypeStr := before
		msgData := after
		var msgType protocol.MessageType

		switch msgTypeStr {
		case "1":
			msgType = protocol.MessageNormal

		case "2":
			msgType = protocol.MessageEmoji

		case "3":
			msgType = protocol.MessageURL

		case "4":
			msgType = protocol.MessageECHO

		default:
			fmt.Printf("Invalid message type: %s\n", msgTypeStr)
			continue
		}

		if err := c.peer.Send(msgType, msgData); err != nil {
			fmt.Printf("Error sending message: %v\n", err)
		}
	}

	if err := scanner.Err(); err != nil {
		fmt.Printf("Error reading input: %v\n", err)
	}
}

func main() {
	reader := bufio.NewReader(os.Stdin)

	fmt.Print("Nickname: ")

	nickname, _ := reader.ReadString('\n')
	nickname = strings.TrimSpace(nickname)

	fmt.Print("Local port: ")

	localPortStr, _ := reader.ReadString('\n')
	localPort, _ := strconv.Atoi(strings.TrimSpace(localPortStr))

	fmt.Print("Remote IP address: ")

	remoteIp, _ := reader.ReadString('\n')
	remoteIp = strings.TrimSpace(remoteIp)

	fmt.Print("Remote port: ")

	remotePortStr, _ := reader.ReadString('\n')
	remotePort, _ := strconv.Atoi(strings.TrimSpace(remotePortStr))

	peer, err := NewPeer(localPort, remoteIp, remotePort, nickname)

	if err != nil {
		fmt.Println(err)
		return
	}

	defer peer.Close()

	fmt.Printf("Connection started on port %d\n\n", localPort)

	chat := NewChat(peer)
	chat.Start()

	fmt.Println("Connection closed")
}
