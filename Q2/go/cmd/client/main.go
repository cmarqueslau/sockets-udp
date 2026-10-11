/*
   Data de criação: 11/10/2026
   Estudante: Gustavo Martins França
   Ponto de entrada do cliente UDP
*/

package main

import (
	"log"
	"net"
	"os"

	"github.com/joho/godotenv"
)

func main() {
	if err := godotenv.Load(); err != nil {
		log.Fatal("Error loading environment")
	}

	addr := os.Getenv("SERVER_ADDR")

	if addr == "" {
		log.Fatal("Provide a valid environment (SERVER_ADDR)")
	}

	serverAddr, err := net.ResolveUDPAddr("udp", addr)

	if err != nil {
		log.Fatalf("Invalid server address: %v", err)
	}

	client := NewClient(serverAddr)
	client.Run()
}
