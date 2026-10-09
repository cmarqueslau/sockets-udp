# Programação com Sockets UDP

Alunos: Caroline Marques Lau, Gustavo Martins França e Maria Eduarda Bambini

Disciplina: Sistemas Distribuídos

## Questão 1 — Chat P2P via UDP

Sistema de chat que permite a troca de mensagens entre clientes utilizando o protocolo UDP.

### Formato das mensagens

| Campo | Tamanho |
|---|---|
| Tipo da mensagem | 1 byte |
| Tamanho do apelido | 1 byte |
| Apelido | 1 a 64 bytes |
| Tamanho da mensagem | 1 byte |
| Mensagem | Variável |

### Tipos de mensagem

- `1` — Mensagem normal
- `2` — Emoji
- `3` — URL
- `4` — ECHO, utilizado para verificar se o usuário está ativo

## Questão 2 — Upload de arquivos via UDP

Sistema que permite transferir arquivos de um cliente para um servidor utilizando o protocolo UDP.

### Funcionamento

1. O cliente envia o nome e o tamanho do arquivo.
2. O arquivo é dividido em pacotes de até 1024 bytes.
3. Os pacotes são enviados ao servidor via UDP.
4. Ao final, o cliente envia o checksum SHA-1 do arquivo.
5. O servidor verifica a integridade do arquivo recebido.
6. Se o checksum corresponder, o arquivo é armazenado na pasta de destino.