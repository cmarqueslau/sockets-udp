/*
   Data última atualização: 11/10/2026
   Estudante: Maria Eduarda Bambini
   Definição das mensagens do protocolo
*/

package common;

import java.nio.charset.StandardCharsets;

/**
* Todas as mensagens do protocolo. Cada tipo é um registro que contém apenas os campos necessários,
* e os construtores compactos rejeitam valores inválidos, de modo que não é possível criar uma mensagem inválida. 
* Números de transmissão sem sinal (sequência uint32, tamanho uint64) são armazenados em um long do Java. 
*/
public sealed interface Message {

    int MAX_FILE_NAME_SIZE = 255;
    int MAX_PAYLOAD_SIZE = 1024;
    int HASH_SIZE = 20; // SHA-1

    long HEADER_SEQ = 0;
    long FIRST_DATA_SEQ = 1;
    long MAX_SEQ = 0xFFFFFFFFL;

    enum Command { ADD_FILE }
    enum Status { SUCCESS, ERROR }

    record Request(Command command) implements Message { }
    record Response(Status status) implements Message { }
    record Header(String fileName, long fileSize) implements Message {
        public Header {
            int nameLength = fileName.getBytes(StandardCharsets.UTF_8).length;

            if (nameLength == 0 || nameLength > MAX_FILE_NAME_SIZE) {
                throw new IllegalArgumentException("invalid file name length: " + nameLength);
            }

            if (fileSize < 0) {
                throw new IllegalArgumentException("invalid file size: " + fileSize);
            }
        }
    }

    /** O cliente envia uma parte (no máximo 1024 bytes) do arquivo. */
    record Data(long seq, byte[] payload) implements Message {
        public Data {
            if (seq < 0 || seq > MAX_SEQ) {
                throw new IllegalArgumentException("sequence out of range: " + seq);
            }

            if (payload.length > MAX_PAYLOAD_SIZE) {
                throw new IllegalArgumentException("payload too large: " + payload.length);
            }
        }
    }

    /** O cliente envia o resumo SHA-1 (20 bytes) do arquivo completo. */
    record Checksum(byte[] hash) implements Message {
        public Checksum {
            if (hash.length != HASH_SIZE) {
                throw new IllegalArgumentException("invalid hash length: " + hash.length);
            }
        }
    }

    /** O servidor confirma o cabeçalho (seq 0) ou um pacote de dados (seu seq). */
    record Ack(long seq) implements Message { }
}