## Sobre o programa

Aplicação de chat P2P em C# que permite enviar e receber mensagens utilizando o protocolo UDP.

## Dependências

- .NET SDK
- Serilog
- Serilog.Sinks.Console

## Restaurar dependências

Na pasta do projeto, execute:

```bash
dotnet restore
```

## Executar

```bash
dotnet run
```

## Configuração

Ao iniciar, informe:

- **Nickname:** nome do usuário.
- **Porta local:** porta UDP em que o cliente receberá mensagens.
- **IP remoto:** endereço IP do outro cliente.
- **Porta remota:** porta UDP do outro cliente.

Para testar dois clientes na mesma máquina, execute o projeto em dois terminais e configure as portas de forma recíproca. Por exemplo:

| Configuração | Cliente 1 | Cliente 2 |
|---|---:|---:|
| Porta local | 5000 | 5001 |
| IP remoto | 127.0.0.1 | 127.0.0.1 |
| Porta remota | 5001 | 5000 |

## Tipos de mensagem

- `1` — Normal
- `2` — Emoji
- `3` — URL
- `4` — ECHO

Digite o número do tipo seguido de um espaço e da mensagem. Exemplo:

```text
1 Olá, tudo bem?
```

Para encerrar o cliente, digite `sair`.