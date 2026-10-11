.PHONY: all run-server run-client build-server build-client build clean

all: build

run-server:
	go run ./cmd/server

run-client:
	go run ./cmd/client

build-server:
	go build -o bin/server ./cmd/server

build-client:
	go build -o bin/client ./cmd/client

build: build-server build-client

clean:
	rm -rf bin/
