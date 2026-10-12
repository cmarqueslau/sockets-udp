# Chat P2P UDP (Java)

* Data última atualização: 11/10/2026
* Estudante: Maria Eduarda Bambini

Chat peer-to-peer desenvolvido em Java com sockets UDP que permite que dois clientes troquem mensagens entre si. 

## Requisitos

- **Java 11 ou superior** (JDK)

## Compilação

Na pasta onde estão os arquivos:

```bash
javac *.java
```

## Execução

Abra um terminal para cada peer e execute em ambos:

```bash
java Main
```

O programa pergunta, nesta ordem:

1. **Seu apelido** (de 1 a 64 bytes)
2. **Porta local** (porta em que este peer vai receber mensagens)
3. **IP destino** (IP do outro peer)
4. **Porta destino** (porta local do outro peer)

### Testando na mesma máquina

Use portas locais diferentes e aponte cada peer para a porta do outro:

| | Peer A | Peer B |
|---|---|---|
| Apelido | `Carol` | `Gu` |
| Porta local | `5000` | `5001` |
| IP destino | `127.0.0.1` | `127.0.0.1` |
| Porta destino | `5001` | `5000` |

### Conectando de outra máquina

Em cada peer, informe como IP destino o IP da máquina do outro peer (por exemplo, `192.168.0.10`). Certifique-se de que a porta UDP usada não está bloqueada pelo firewall.

## Formato da mensagem

Cada mensagem é enviada em um datagrama UDP com o seguinte formato:

| Campo | Tamanho | Descrição |
|---|---|---|
| Tipo da mensagem | 1 byte | Valor de 1 a 4 (veja a tabela abaixo) |
| Tamanho do apelido (`tam_apl`) | 1 byte | Entre 1 e 64 |
| Apelido | `tam_apl` bytes | Apelido de quem enviou (UTF-8) |
| Tamanho da mensagem (`tam_msg`) | 1 byte | Entre 0 e 255 |
| Mensagem | `tam_msg` bytes | Conteúdo da mensagem (UTF-8) |

Os tamanhos são contados em bytes e não em caracteres: acentos e emojis ocupam mais de 1 byte em UTF-8.

## Tipos de mensagem

| Tipo | Nome | Descrição | Exibição no receptor |
|---|---|---|---|
| `1` | Normal | Mensagem de texto comum | `apelido: mensagem` |
| `2` | Emoji | Mensagem com emoji | `apelido [emoji]: mensagem` |
| `3` | URL | Link (validado antes de enviar) | `apelido [link]: mensagem` |
| `4` | ECHO | Verifica se o outro usuário está ativo | `[ECHO] Usuario está ativo.` |

### Como funciona o ECHO

1. O peer que quer testar envia uma mensagem do tipo `4`.
2. Quem recebe devolve o mesmo pacote ao remetente, sem exibir mais nada além do aviso `[ECHO] Recebido de ...`.
3. Ao receber o pacote de volta, o remetente sabe que o outro usuário está ativo.
4. Se não houver resposta em 10 segundos, é exibido `[ECHO] Sem resposta: usuario inativo.`

Para que o ECHO funcione corretamente, os dois peers devem usar apelidos diferentes.

## Menu de envio

Depois de configurado, o peer exibe um menu para escolher o tipo da mensagem:

| Opção | Ação |
|---|---|
| `1` | Enviar mensagem normal |
| `2` | Enviar emoji |
| `3` | Enviar URL |
| `4` | Enviar ECHO |
| `0` | EXIT (encerra o peer e fecha o socket) |

As mensagens recebidas aparecem no terminal onde o programa foi executado.

## Validações

- Apelido com 1 a 64 bytes
- Mensagem com no máximo 255 bytes
- URLs inválidas não são enviadas
- Pacotes recebidos com tipo inválido, tamanhos fora do limite ou menores que o esperado são descartados