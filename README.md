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

### Especificação do protocolo

#### 1. Comandos suportados
- `0x01` - **ADDFILE <caminho_do_arquivo>**: Inicia o upload de um arquivo para o servidor, fragmentando-o em pacotes de até 1024 bytes. Para cada pacote haverá uma confirmação de envio (ACK). Ao final, é enviado um checksum para validar o recebimento completo e correto do arquivo enviado.

#### 2. Requisição (Cliente -> Servidor)

Durante a operação `ADDFILE`, o cliente envia os seguintes pacotes para o servidor:

##### 2.1. Requisição (Request)
Identifica a operação principal e o caminho base.
- `1 byte`: MessageType (`0x01`)
- `1 byte`: Command (`0x01`)

##### 2.2. Cabeçalho (Header)
Enviado para preparar o servidor com os metadados do arquivo.
- `1 byte`: MessageType (`0x03` HEADER)
- `1 byte`: NameSize
- `8 bytes`: FileSize
- `0 - 255 bytes`: FileName

##### 2.3. Dados (Data)
Enviado sequencialmente; o cliente aguarda o ACK do último pacote enviado antes de enviar o próximo.
- `1 byte`: MessageType (`0x04` DATA)
- `4 bytes`: Seq
- `2 bytes`: PayloadSize
- `1 - 1024 bytes`: Payload

##### 2.4. Validação (Checksum)
Enviado após a confirmação do último pacote de dados.
- `1 byte`: MessageType (`0x05` CHECKSUM)
- `20 bytes`: Hash (SHA-1 calculado a partir do arquivo sendo enviado)

#### 3. Resposta (Servidor -> Cliente)

##### 3.1. Confirmação (ACK)
Enviado pelo servidor para confirmar o recebimento de um pacote em trânsito (Header ou Data).
- `1 byte`: MessageType (`0x06` ACK)
- `4 bytes`: Seq (`0` para Header; `Data.Seq` para Data).

##### 3.2. Resposta Final (Response)
Enviado pelo servidor em resposta ao pacote Checksum para confirmar o sucesso ou falha na validação e armazenamento do arquivo.
- `1 byte`: MessageType (`0x02`)
- `1 byte`: Status (`0x01` SUCCESS; `0x02` ERROR)

#### 4. Timeout (Cliente)
- O cliente inicia um temporizador (ex. 200ms) após o envio de qualquer pacote.
- Se a confirmação esperada (ACK para Header/Data; Response para Checksum) não for recebida antes do temporizador atingir seu limite, o cliente reenvia o pacote.
- Se houverem muitos reenvios consecutivos (ex. mais de 5), é encerrada a transferência.
