## Sobre o programa

Implementar um sistema cliente-servidor para transferência de arquivos utilizando o protocolo UDP em C#. Como o UDP não garante a entrega nem a ordem dos pacotes, o protocolo implementa confirmações (ACK), retransmissões e verificação de integridade por SHA-1.

## Estrutura

- `protocol/`: lógica do protocolo de comunicação.
- `server/`: recebimento e armazenamento dos arquivos.
- `client/`: seleção e envio dos arquivos.

## Configuração

Crie um arquivo `.env` com as configurações necessárias. Use o `.env.example` como referência.

- `SERVER_RECEIVE_DIRECTORY`: diretório onde os arquivos recebidos serão salvos.
- `CLIENT_SEND_DIRECTORY`: diretório dos arquivos disponíveis para envio.

## Restaurar dependências

Execute na pasta `csharp`:

```bash
dotnet restore protocol/protocol.csproj
dotnet restore server/server.csproj
dotnet restore client/client.csproj
```

## Compilar

```bash
dotnet build protocol/protocol.csproj
dotnet build server/server.csproj
dotnet build client/client.csproj
```

## Executar

Inicie primeiro o servidor:

```bash
dotnet run --project server/server.csproj
```

Depois, inicie o cliente:

```bash
dotnet run --project client/client.csproj
```

O servidor solicita a porta de escuta, e o cliente solicita o IP e a porta do servidor para realizar a conexão.