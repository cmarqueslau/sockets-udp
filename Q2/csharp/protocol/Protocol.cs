/*
    Data de criação: 12/10/2026
    Aluna: Caroline Marques Lau
    Especificações sobre o envio e recebimento das mensagens
*/

using System.Buffers.Binary;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Security.Cryptography;
using Serilog;

public enum MessageType : byte
{
    REQUEST = 0x01,
    RESPONSE = 0x02,
    HEADER = 0x03,
    DATA = 0x04,
    CHECKSUM = 0x05,
    ACK = 0x06
}
public enum Command : byte
{
    ADDFILE = 0x01
}

public enum ResponseStatus : byte
{
    SUCCESS = 0x01,
    ERROR = 0x02
}
public struct FileHeader
{
    public MessageType MessageType;
    public byte FileNameSize; // Representa a quantidade de bytes do pŕoximo campo
    public ulong FileSize; // Para cálculo da quantidade de pacotes a serem recebidos
    public byte[] FileName;
}
public struct FilePackage
{
    public MessageType MessageType;
    public uint SequenceNumber;
    public ushort DataSize;
    public byte[] Data;
}

public struct Checksum
{
    public MessageType MessageType;
    public byte[] Hash; // 20 bytes
}

public struct Ack
{
    public MessageType MessageType;
    public uint SequenceNumber;
}

public class Protocol
{
    private readonly UdpClient udp;
    private readonly IPEndPoint? destination;
    private readonly string? receiveDirectory;

    public Protocol(
        UdpClient udp,
        IPEndPoint? destination = null,
        string? receiveDirectory = null)
    {
        this.udp = udp;
        this.destination = destination;
        this.receiveDirectory = receiveDirectory;

        if (destination != null)
            this.udp.Client.ReceiveTimeout = 200;
        Log.Information(
        "Protocol inicializado. Modo: {Mode}",
        destination != null ? "Cliente" : "Servidor");
    }
    private FileStream? outputFile;
    private ulong expectedFileSize;
    private ulong receivedBytes;
    private uint expectedSequence = 1;
    private string? outputPath;
    public void SendFile(string fileName, byte[] file) // Coordena a transferência de dados e o checksum
    {
        try
        {
            SendRequest();
            SendHeader(fileName, (ulong)file.Length);
            SendData(file);
            SendChecksum(file);

            Log.Information("Arquivo enviado com sucesso: {FileName}", fileName);
        }
        catch (TimeoutException ex)
        {
            Log.Error(ex, "Timeout ao enviar o arquivo {FileName}.", fileName);
        }
        catch (IOException ex)
        {
            Log.Error(ex, "Erro no recebimento do arquivo pelo servidor.");
        }
    }
    public void RecvFile() // Recebe e processa os pacotes
    {
        IPEndPoint remote =
            new(IPAddress.Any, 0);
        while (true)
        {
            byte[] packet = udp.Receive(ref remote);
            if (packet.Length == 0)
                continue;

            MessageType type = (MessageType)packet[0];

            switch (type)
            {
                case MessageType.REQUEST:
                    if (packet.Length == 2 && packet[1] == (byte)Command.ADDFILE)
                    {
                        Log.Information("Request ADDFILE recebido.");

                        SendAck(remote, 0);
                    }
                    else
                    {
                        Log.Warning("REQUEST inválido.");
                    }
                    break;

                case MessageType.HEADER:
                    ReceiveHeader(packet, remote);
                    break;
                case MessageType.DATA:
                    ReceiveData(packet, remote);
                    break;
                case MessageType.CHECKSUM:
                    ReceiveChecksum(packet, remote);
                    break;
            }
        }
    }
    public void SendHeader(string fileName, ulong fileSize) // Envia o nome e o tamanho do arquivo (espera um ACK)
    {
        byte[] nameBytes = Encoding.UTF8.GetBytes(fileName);
        if (nameBytes.Length > 255)
        {
            throw new ArgumentException("O nome do arquivo enviado é muito longo");
        }
        byte[] header = new byte[10 + nameBytes.Length]; // 1 do tipo + 1 do tamanho do nome + 8 do número de bytes do arquivo
        header[0] = (byte)MessageType.HEADER;
        header[1] = (byte)nameBytes.Length;

        BinaryPrimitives.WriteUInt64BigEndian(
            header.AsSpan(2, 8),
            fileSize
        );
        nameBytes.CopyTo(header, 10);
        SendWithRetry(header, 0);
    }
    private void ReceiveHeader(byte[] packet, IPEndPoint remote)
    {
        if (packet.Length < 10)
        {
            Log.Information("HEADER inválido: pacote muito pequeno.");
            return;
        }
        byte nameSize = packet[1];
        if (packet.Length != 10 + nameSize)
        {
            Log.Information("HEADER inválido: tamanho incorreto.");
            return;
        }

        expectedFileSize =
            BinaryPrimitives.ReadUInt64BigEndian(
                packet.AsSpan(2, 8));

        string fileName =
            Encoding.UTF8.GetString(packet, 10, nameSize);

        fileName = Path.GetFileName(fileName);

        if (string.IsNullOrWhiteSpace(fileName))
        {
            Log.Information("HEADER inválido: nome vazio.");
            return;
        }

        if (string.IsNullOrWhiteSpace(receiveDirectory))
        {
            Log.Information("Diretório de recebimento não configurado.");
            return;
        }

        Directory.CreateDirectory(receiveDirectory);

        outputPath = Path.Combine(receiveDirectory, fileName);

        outputFile?.Dispose(); // Fecha o fluxo e libera os recursos caso não seja nulo

        outputFile = new FileStream(
            outputPath, // Caminho do arquivo
            FileMode.Create, // Cria o arquivo se não existir ou sobrescreve seu conteúdo
            FileAccess.Write); // Abre para escrita

        receivedBytes = 0;
        expectedSequence = 1;

        Log.Information(
            "Arquivo iniciado: {FileName}, tamanho: {FileSize} bytes.",
            fileName,
            expectedFileSize);

        SendAck(remote, 0);
    }
    public void SendRequest() // Solicita o ADDFILE
    {
        byte[] request =
        {
            (byte)MessageType.REQUEST,
            (byte)Command.ADDFILE
        };

        udp.Send(request, request.Length, destination);
    }
    private bool WaitForAck(uint expectedSequence) // Espera o recebimento de um ACK com compara o número da sequência
    {
        try
        {
            IPEndPoint remote = new(IPAddress.Any, 0);
            byte[] response = udp.Receive(ref remote);

            if (response.Length != 5)
                return false;

            if (response[0] != (byte)MessageType.ACK)
                return false;

            uint sequence = BinaryPrimitives.ReadUInt32BigEndian(
                response.AsSpan(1, 4));

            return sequence == expectedSequence;
        }
        catch (SocketException)
        {
            return false;
        }
    }
    private void SendWithRetry(byte[] packet, uint expectedSequence) // Envia até receber o ACK
    {
        const int maxAttempts = 5;

        for (int attempt = 0; attempt < maxAttempts; attempt++)
        {
            udp.Send(packet, packet.Length, destination!);

            if (WaitForAck(expectedSequence))
                return;
        }

        throw new TimeoutException(
            $"ACK {expectedSequence} não recebido.");
    }
    private void SendDataPacket(uint sequence, byte[] data)
    {
        if (data.Length == 0 || data.Length > 1024)
            throw new ArgumentException("Tamanho inválido do pacote.");

        byte[] packet = new byte[7 + data.Length];

        packet[0] = (byte)MessageType.DATA;

        BinaryPrimitives.WriteUInt32BigEndian(
            packet.AsSpan(1, 4), sequence);

        BinaryPrimitives.WriteUInt16BigEndian(
            packet.AsSpan(5, 2), (ushort)data.Length);

        data.CopyTo(packet, 7);

        SendWithRetry(packet, sequence);
    }
    private void SendData(byte[] file)
    {
        const int chunkSize = 1024;
        uint sequence = 1;

        for (int offset = 0; offset < file.Length; offset += chunkSize)
        {
            int size = Math.Min(chunkSize, file.Length - offset);

            byte[] chunk = new byte[size];
            Array.Copy(file, offset, chunk, 0, size);

            SendDataPacket(sequence, chunk);

            sequence++;
        }
    }
    private void SendAck(IPEndPoint remote, uint sequence)
    {
        byte[] ack = new byte[5];

        ack[0] = (byte)MessageType.ACK;

        BinaryPrimitives.WriteUInt32BigEndian(
            ack.AsSpan(1, 4), sequence);

        udp.Send(ack, ack.Length, remote);
    }
    private void ReceiveData(byte[] packet, IPEndPoint remote)
    {
        if (outputFile == null)
        {
            Log.Warning("DATA recebido, mas nenhum arquivo está aberto.");
            return;
        }

        if (packet.Length < 7)
        {
            Log.Warning("DATA inválido: pacote menor que 7 bytes.");
            return;
        }

        uint sequence =
            BinaryPrimitives.ReadUInt32BigEndian(
                packet.AsSpan(1, 4));

        ushort payloadSize =
            BinaryPrimitives.ReadUInt16BigEndian(
                packet.AsSpan(5, 2));

        if (payloadSize == 0 || payloadSize > 1024)
        {
            Log.Warning(
                "DATA inválido: tamanho do conteúdo {PayloadSize}.",
                payloadSize);
            return;
        }

        if (packet.Length != 7 + payloadSize)
        {
            Log.Warning(
                "DATA inválido: tamanho recebido {PacketLength}, esperado {ExpectedLength}.",
                packet.Length,
                7 + payloadSize);
            return;
        }

        if (sequence < expectedSequence)
        {
            Log.Information(
                "DATA duplicado: sequência {Sequence}. Reenviando ACK.",
                sequence);

            SendAck(remote, sequence);
            return;
        }

        if (sequence > expectedSequence)
        {
            Log.Warning(
                "DATA fora de ordem: recebido {Sequence}, esperado {ExpectedSequence}.",
                sequence,
                expectedSequence);
            return;
        }

        try
        {
            outputFile.Write(packet, 7, payloadSize);
        }
        catch (IOException ex)
        {
            Log.Error(ex, "Erro ao gravar os dados no arquivo.");
            return;
        }

        receivedBytes += payloadSize;
        expectedSequence++;

        SendAck(remote, sequence);

        Log.Information(
            "DATA {Sequence} recebido: {PayloadSize} bytes. Total: {ReceivedBytes}/{ExpectedFileSize}.",
            sequence,
            payloadSize,
            receivedBytes,
            expectedFileSize);
    }
    private void ReceiveChecksum(byte[] packet, IPEndPoint remote)
    {
        if (packet.Length != 21)
        {
            Log.Warning("CHECKSUM inválido: tamanho incorreto.");
            return;
        }

        if (outputFile == null || outputPath == null)
        {
            Log.Warning("CHECKSUM recebido sem arquivo aberto.");
            return;
        }

        if (receivedBytes != expectedFileSize)
        {
            Log.Warning(
                "Arquivo incompleto: recebido {ReceivedBytes} de {ExpectedFileSize} bytes.",
                receivedBytes,
                expectedFileSize);

            return;
        }

        try
        {
            outputFile.Dispose();
            outputFile = null;

            byte[] receivedHash = packet[1..21];
            byte[] fileBytes = File.ReadAllBytes(outputPath);
            byte[] calculatedHash = SHA1.HashData(fileBytes);

            if (!CryptographicOperations.FixedTimeEquals(
                    receivedHash, calculatedHash))
            {
                Log.Warning("Checksum SHA-1 não confere.");
                SendFinalResponse(remote, false);
                return;
            }

            Log.Information("Arquivo recebido e checksum validado.");
            SendFinalResponse(remote, true);
        }
        catch (IOException ex)
        {
            Log.Error(ex, "Erro ao verificar o arquivo recebido.");
            SendFinalResponse(remote, false);
        }
    }
    private void SendFinalResponse(IPEndPoint remote, bool success)
    {
        byte[] packet =
        [
            (byte)MessageType.RESPONSE,
            success ? (byte)0x01 : (byte)0x02
        ];

        udp.Send(packet, packet.Length, remote);

        Log.Information(
            "Resposta final enviada: {Status}.",
            success ? "SUCCESS" : "ERROR");
    }
    private void SendChecksum(byte[] file)
    {
        byte[] hash = SHA1.HashData(file);

        byte[] packet = new byte[21];

        packet[0] = (byte)MessageType.CHECKSUM;

        Buffer.BlockCopy(hash, 0, packet, 1, hash.Length);

        const int maxAttempts = 5;
        for (int attempt = 0; attempt < maxAttempts; attempt++)
        {
            udp.Send(packet, packet.Length, destination!);
            if (WaitForFinalResponse())
                return;
        }
        throw new TimeoutException(
            "Resposta final do servidor não recebida.");
    }

    private bool WaitForFinalResponse()
    {
        IPEndPoint remote = new(IPAddress.Any, 0);

        while (true)
        {
            try
            {
                byte[] response = udp.Receive(ref remote);

                if (response.Length != 2 || response[0] != 0x02)
                {
                    Log.Debug(
                        "Pacote inesperado ao aguardar resposta final. " +
                        "Tamanho: {Length}, tipo: {Type}.",
                        response.Length,
                        response.Length > 0 ? response[0] : -1);

                    continue;
                }

                if (response[1] == 0x01)
                {
                    Log.Information("Arquivo recebido com sucesso.");
                    return true;
                }

                if (response[1] == 0x02)
                {
                    throw new IOException(
                        "O servidor rejeitou o arquivo.");
                }

                Log.Warning("Status da resposta final desconhecido.");
                return false;
            }
            catch (SocketException ex)
                when (ex.SocketErrorCode == SocketError.TimedOut)
            {
                Log.Warning(ex, "Timeout aguardando a resposta final.");
                return false;
            }
        }
    }
}