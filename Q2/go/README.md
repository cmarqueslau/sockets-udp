## Sobre o programa

Aplicação cliente-servidor desenvolvida em Go para a transferência de arquivos para um servidor UDP.

Os comandos executados em uma sessão serão registrados em `logs/server.log`.

## Dependências

- Go
- Makefile

## Configuração

Crie uma cópia de `.env.example`, a renomeie para `.env` e altere as variáveis conforme necessário.

## Como executar

Compile o servidor e o cliente:

```bash
make build
```

Inicie o servidor (terminal 1):

```bash
make run-server
```

Inicie o cliente (terminal 2):

```bash
make run-client
```
