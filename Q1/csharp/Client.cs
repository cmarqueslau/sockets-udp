/*
    Data de criação: 08/10/2026
    Aluna: Caroline Marques Lau
    Arquivo do cliente que se conecta a outro e envia mensagens
*/

using System;
using System.Net;
using System.Net.Sockets;
using System.Text;
using Serilog;
using System.Threading.Tasks;

class Program
{
    static async Task Main()
    {
        Log.Logger = new LoggerConfiguration().WriteTo.Console().CreateLogger();
        Log.Information("Cliente iniciado");
        Socket socket = new(
            AddressFamily.InterNetwork, // Uso do IPv4
            SocketType.Dgram, // Comunicação por datagramas
            ProtocolType.Udp);
        Task? receivedTask = null; // O ? indica que a variável pode receber null

        try
        {
            Console.ForegroundColor = ConsoleColor.DarkMagenta;
            Console.WriteLine("------------------------------------------------------------------");
            Console.ResetColor();
            Console.Write("Seu apelido: ");
            string nickname = Console.ReadLine() ?? "";

            Console.Write("Sua porta local: ");
            int localPort = int.Parse(Console.ReadLine() ?? "");

            Console.Write("IP do destinatário: ");
            string remoteIp = Console.ReadLine() ?? "";

            Console.Write("Porta do destinatário: ");
            int remotePort = int.Parse(Console.ReadLine() ?? "");
            Console.ForegroundColor = ConsoleColor.DarkMagenta;
            Console.WriteLine("------------------------------------------------------------------");
            Console.ResetColor();
            socket.Bind(new IPEndPoint(IPAddress.Any, localPort));
            // Recebe pacotes destinados à porta de qualquer IPv4 local

            EndPoint destination = new IPEndPoint(
                IPAddress.Parse(remoteIp),
                remotePort);

            Log.Information("Cliente iniciado na porta {Port}", localPort);
            Protocol protocol = new(socket, destination);

            Log.Information("Tipos de mensagens permitidos: 1 - Normal | 2 - Emoji | 3 - URL | 4 - ECHO | sair");
            Log.Information("Digite o tipo e a mensagem (exemplo: 1 Oi!)");

            // Task de recepção
            receivedTask = Task.Run(() =>
            {
                while (true)
                {
                    try
                    {
                        Message received = protocol.RecvMessage();
                        if (received.MessageType == MessageType.Echo)
                        {
                            received.MessageType = MessageType.Standard;
                            protocol.SendMessage(received);
                        }
                        string sender = Encoding.UTF8.GetString(received.Nick);
                        string content = Encoding.UTF8.GetString(received.MessageContent);
                        Log.Information(
                            "Mensagem recebida de {Nickname}. Tipo: {Type}",
                            sender,
                            received.MessageType);

                        Console.ForegroundColor = ConsoleColor.Cyan;
                        Console.Write($"[{received.MessageType}] ");

                        Console.ForegroundColor = ConsoleColor.Yellow;
                        Console.Write($"{sender}: ");

                        Console.ForegroundColor = ConsoleColor.White;
                        Console.WriteLine(content);

                        Console.ResetColor();
                    }
                    catch (SocketException ex)
                    {
                        Log.Debug(ex, "Operação de recepção interrompida.");
                        break;
                    }
                    catch (ObjectDisposedException)
                    {
                        break;
                    }
                    catch (Exception ex)
                    {
                        Log.Error(ex, "Erro ao receber mensagem");
                        break;
                    }
                }
            });

            while (true)
            {
                string input = Console.ReadLine() ?? "";

                if (input.Equals("sair", StringComparison.OrdinalIgnoreCase))
                    break;
                int separator = input.IndexOf(' '); // Encontra o espaço
                if (separator == -1)
                {
                    Log.Warning("Digite o número do tipo e depois a mensagem.");
                    continue;
                }
                string type = input[..separator];
                string content = input[(separator + 1)..];
                MessageType messageType;
                switch (type)
                {
                    case "1":
                        messageType = MessageType.Standard;
                        break;
                    case "2":
                        messageType = MessageType.Emoji;
                        break;
                    case "3":
                        messageType = MessageType.URL;
                        break;
                    case "4":
                        messageType = MessageType.Echo;
                        break;
                    default:
                        Log.Warning("Tipo de mensagem inválido: {Choice}", type);
                        Log.Information("Tipos de mensagens permitidos: 1 - Normal | 2 - Emoji | 3 - URL | 4 - ECHO | sair");
                        Log.Information("Digite o tipo e a mensagem (exemplo: 1 Oi!)");
                        continue;
                }
                Message message = protocol.CreateMessage(
                    messageType,
                    nickname,
                    content
                );
                protocol.SendMessage(message);

                Log.Information(
                "Mensagem enviada. Tipo: {Type}",
                messageType);
            }
        }
        catch (Exception ex)
        {
            Log.Error(ex, "Erro inesperado.");
        }
        finally
        {
            socket?.Close();
            if (receivedTask is not null)
            {
                try
                {
                    await receivedTask;
                }
                catch (Exception ex)
                {
                    Log.Debug(ex, "Tarefa de recepção finalizada");
                }
            }
            Log.Information("Cliente encerrado");
            Log.CloseAndFlush();
        }
    }
}