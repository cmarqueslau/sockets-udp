# Upload de arquivos via UDP (Java)

* Data última atualização: 12/10/2026
* Estudante: Maria Eduarda Bambini

Sistema cliente/servidor em Java que transfere arquivos por protocolo UDP em partes de 1024 bytes. O servidor confere a integridade do arquivo com SHA-1
e só então o guarda na pasta padrão (`uploads/`).

## Requisitos, compilação e execução

Java 21 ou superior.

```bash
# compilar
javac -d out src/common/*.java src/server/*.java src/client/*.java

# servidor
java -cp out server.Server

# cliente (outro terminal)
java -cp out client.Client
```

## Protocolo

Todo número é big-endian. Cada datagrama carrega exatamente uma mensagem, em que o primeiro byte é o tipo.

| Mensagem | Tipo | Layout (bytes) | Quem envia |
|----------|------|----------------|------------|
| Request  | `0x01` | `tipo(1)` `comando(1)` | cliente |
| Response | `0x02` | `tipo(1)` `status(1)` | servidor |
| Header   | `0x03` | `tipo(1)` `tamNome(1)` `tamArquivo(8)` `nome(tamNome)` | cliente |
| Data     | `0x04` | `tipo(1)` `seq(4)` `tamPayload(2)` `payload(≤1024)` | cliente |
| Checksum | `0x05` | `tipo(1)` `sha1(20)` | cliente |
| ACK      | `0x06` | `tipo(1)` `seq(4)` | servidor |

Comando: `0x01` = `ADD_FILE`. Status: `0x01` = sucesso, `0x02` = erro.
O nome do arquivo é UTF-8 (até 255 bytes). O maior datagrama é o Data: 1 + 4 + 2 + 1024 = 1031 bytes.

### Implementação

* **Pare-e-espere:** o cliente só envia a próxima mensagem depois de receber a resposta da anterior;
* **Timeout e retransmissão:** sem resposta em 500 ms, a mensagem é reenviada (até 10 tentativas);
* **Duplicatas são inofensivas:** o servidor só grava o Data com o `seq` esperado; um `seq` já
  gravado apenas recebe o ACK de novo (o ACK anterior foi perdido); um `seq` à frente é ignorado;
  Request, Header e Checksum repetidos também recebem a mesma resposta de antes;
* **Sessão por cliente:** identificada pelo endereço (IP:porta), então vários clientes podem enviar ao mesmo tempo.
  O mesmo cliente pode enviar vários arquivos em sequência, e antes de cada envio ele descarta
  datagramas atrasados do envio anterior;
* **Limpeza:** upload parado por 15 s é descartado (o `.part` é apagado).

### Ressalvas

* O SHA-1 é calculado de forma incremental enquanto os dados chegam. No Checksum o servidor compara
  com o valor enviado pelo cliente; se for igual (e o tamanho bater com o do Header), o `.part` é
  renomeado para o nome final;
* Nomes com `/`, `\`, `..` ou fora da pasta padrão são recusados;
* Tamanho anunciado maior que 1 GiB é recusado, e dados além do tamanho anunciado abortam o upload;
* Se já existir um arquivo com o mesmo nome, ele é substituído.