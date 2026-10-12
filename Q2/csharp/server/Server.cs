/*
    Data de criação: 12/10/2026
    Aluna: Caroline Marques Lau
    Arquivo do servidor que recebe o arquivo
*/
using System.Net;
using System.Net.Sockets;
using Serilog;
using DotNetEnv;

public class Server
{
    public static void Main()
    {
        Log.Logger = new LoggerConfiguration()
            .WriteTo.Console()
            .CreateLogger();

        Log.Information("Digite a porta em que o servidor irá escutar:");

        if (!int.TryParse(Console.ReadLine(), out int port) ||
            port < 1 || port > 65535)
        {
            Log.Error("Porta inválida.");
            return;
        }
        using UdpClient udp = new(port);

        Env.Load();

        string receiveDirectory =
            Environment.GetEnvironmentVariable("SERVER_RECEIVE_DIRECTORY")
            ?? "../uploads";

        Protocol protocol = new(
            udp,
            null,
            receiveDirectory);

        Log.Information("Servidor iniciado na porta {Port}.", port);

        protocol.RecvFile();
    }
}