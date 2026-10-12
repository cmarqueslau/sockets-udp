/*
    Data de criação: 12/10/2026
    Aluna: Caroline Marques Lau
    Arquivo do cliente que envia arquivos
*/

using DotNetEnv;
using Serilog;
using System.Net;
using System.Net.Sockets;

public class Program
{
    public static void Main()
    {
        Log.Logger = new LoggerConfiguration()
            .WriteTo.Console()
            .CreateLogger();

        Env.Load();

        string sendDirectory =
            Environment.GetEnvironmentVariable("CLIENT_SEND_DIRECTORY")
            ?? "./FilesToSend";

        if (!Directory.Exists(sendDirectory))
        {
            Log.Error(
                "O diretório de envio não existe: {Directory}",
                sendDirectory);

            return;
        }

        Log.Information(
            "Diretório de envio encontrado: {Directory}",
            sendDirectory);

        Log.Information("Digite o IP do servidor:");
        string? serverIp = Console.ReadLine();

        if (!IPAddress.TryParse(serverIp, out IPAddress? address))
        {
            Log.Error("IP inválido.");
            return;
        }

        Log.Information("Digite a porta do servidor:");

        if (!int.TryParse(Console.ReadLine(), out int serverPort) ||
            serverPort < 1 || serverPort > 65535)
        {
            Log.Error("Porta inválida.");
            return;
        }

        IPEndPoint serverEndpoint = new(address, serverPort);

        using UdpClient udp = new();

        Protocol protocol = new(
            udp,
            destination: serverEndpoint);

        Log.Information(
            "Cliente configurado para {ServerIp}:{ServerPort}.",
            serverIp,
            serverPort);

        while (true)
        {
            string[] files = Directory.GetFiles(sendDirectory);

            if (files.Length == 0)
            {
                Log.Warning(
                    "Nenhum arquivo encontrado em {Directory}.",
                    sendDirectory);
                break;
            }

            Log.Information("Arquivos disponíveis:");

            for (int i = 0; i < files.Length; i++)
            {
                Log.Information("{Number}. {FileName}",
                    i + 1,
                    Path.GetFileName(files[i]));
            }

            Log.Information(
                "Digite o número do arquivo que deseja enviar (0 para sair):");

            if (!int.TryParse(Console.ReadLine(), out int choice) ||
                choice < 0 || choice > files.Length)
            {
                Log.Warning("Opção inválida.");
                continue;
            }

            if (choice == 0)
            {
                Log.Information("Encerrando o cliente.");
                break;
            }

            string selectedFile = files[choice - 1];

            try
            {
                byte[] fileBytes = File.ReadAllBytes(selectedFile);
                string fileName = Path.GetFileName(selectedFile);

                Log.Information(
                    "Enviando {FileName} ({FileSize} bytes)...",
                    fileName,
                    fileBytes.Length);

                protocol.SendFile(fileName, fileBytes);
            }
            catch (IOException ex)
            {
                Log.Error(ex, "Erro ao ler o arquivo selecionado.");
            }
        }
    }
}