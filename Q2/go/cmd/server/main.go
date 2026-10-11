/*
   Data de criação: 11/10/2026
   Estudante: Gustavo Martins França
   Ponto de entrada do servidor UDP
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
	baseDir := os.Getenv("BASE_DIR")

	if addr == "" || baseDir == "" {
		log.Fatal("Provide a valid environment")
	}

	// creates storage dir
	if err := os.MkdirAll(baseDir, os.ModePerm); err != nil {
		log.Fatalf("Error creating directory for clients: %v", err)
	}

	// creates logs dir
	if err := os.MkdirAll("logs/", os.ModePerm); err != nil {
		log.Fatalf("Error creating logs directory: %v", err)
	}

	logFile, err := os.OpenFile("logs/server.log", os.O_APPEND|os.O_CREATE|os.O_WRONLY, os.ModePerm)

	if err != nil {
		log.Fatalf("Could not open log file: %v", err)
	}

	defer logFile.Close()

	log.SetOutput(logFile)

	udpAddr, err := net.ResolveUDPAddr("udp", addr)

	if err != nil {
		log.Fatalf("Invalid UDP address: %v", err)
	}

	server := NewServer(udpAddr, baseDir)
	server.Run()
}
