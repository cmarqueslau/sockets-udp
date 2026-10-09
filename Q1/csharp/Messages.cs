/*
    Data de criação: 08/10/2026
    Aluna: Caroline Marques Lau
    Especificações sobre o envio e recebimento das mensagens
*/

using System.Net;
using System.Net.Sockets;
using System.Text;

public enum MessageType : byte
{
    Standard = 0x01,
    Emoji = 0x02,
    URL = 0x03,
    Echo = 0x04
}

public struct Message
{
    public MessageType MessageType;
    public byte NickSize;
    public byte[] Nick;
    public byte MessageSize;
    public byte[] MessageContent;
}

public class Protocol(Socket socket, EndPoint destination)
{
    private readonly Socket socket = socket;
    private readonly EndPoint destination = destination;
    public EndPoint? LastSender { get; private set; }

    public void SendMessage(Message message)
    {
        if (message.Nick == null || message.MessageContent == null)
        {
            throw new InvalidDataException(
                "O apelido e o conteúdo da mensagem não podem ser nulos.");
        }
        if (!Enum.IsDefined(typeof(MessageType), message.MessageType))
        {
            throw new InvalidDataException("Tipo de mensagem inválido.");
        }
        if (message.Nick.Length < 1 || message.Nick.Length > 64)
        {
            throw new InvalidDataException(
                "O apelido deve ter entre 1 e 64 bytes.");
        }
        if (message.NickSize != message.Nick.Length)
        {
            throw new InvalidDataException(
                "NickSize não corresponde ao tamanho real do apelido.");
        }
        if (message.MessageContent.Length > 255)
        {
            throw new InvalidDataException(
                "A mensagem não pode ultrapassar 255 bytes.");
        }
        if (message.MessageSize != message.MessageContent.Length)
        {
            throw new InvalidDataException(
                "MessageSize não corresponde ao tamanho real do conteúdo.");
        }
        int offset = 0;
        int packageSize = 3 + message.Nick.Length + message.MessageContent.Length;
        byte[] package = new byte[packageSize];
        package[offset++] = (byte)message.MessageType;
        package[offset++] = message.NickSize;
        Array.Copy(message.Nick, 0, package, 2, message.NickSize);
        offset += message.NickSize;
        package[offset++] = message.MessageSize;
        Array.Copy(message.MessageContent, 0, package, offset, message.MessageSize);
        socket.SendTo(package, LastSender ?? destination);
    }
    public Message RecvMessage()
    {
        byte[] message = new byte[322]; // tamanho máximo do pacote Message
        EndPoint sender = new IPEndPoint(IPAddress.Any, 0);
        int packageSize = socket.ReceiveFrom(message, ref sender);
        LastSender = sender;
        // O pacote precisa ter pelo menos:
        // tipo (1) + tamanho do apelido (1)
        // + tamanho da mensagem (1) + apelido (1 byte mínimo)
        // + mensagem (0 bytes permitido nesta validação inicial)
        if (packageSize < 4)
        {
            throw new InvalidDataException("O tamanho do pacote recebido não é suficiente para conter todos os campos");
        }

        byte messageType = message[0];
        if (!Enum.IsDefined(typeof(MessageType), messageType))
        {
            throw new InvalidDataException("Tipo de mensagem inválido");
        }
        MessageType type = (MessageType)messageType;
        byte nickSize = message[1];
        if (nickSize < 1 || nickSize > 64)
        {
            throw new InvalidDataException("Tamanho de apelido inválido.");
        }
        // Verifica se existem bytes suficientes para o apelido
        // e para o campo que indica o tamanho da mensagem.
        int messageSizeIndex = 2 + nickSize;
        if (packageSize <= messageSizeIndex)
        {
            throw new InvalidDataException("Pacote incompleto: falta o tamanho da mensagem.");
        }
        byte[] nick = new byte[nickSize];
        Array.Copy(message, 2, nick, 0, nickSize);

        int contentIndex = messageSizeIndex + 1;
        byte messageSize = message[nickSize + 2];

        // Verifica se o conteúdo cabe no pacote recebido.
        if (packageSize != contentIndex + messageSize)
        {
            throw new InvalidDataException("Tamanho do pacote incompatível com os campos declarados.");
        }
        byte[] messageContent = new byte[messageSize];
        Array.Copy(message, nickSize + 3, messageContent, 0, messageSize);
        Message m = new()
        {
            MessageType = type,
            NickSize = nickSize,
            Nick = nick,
            MessageSize = messageSize,
            MessageContent = messageContent,
        };
        return m;
    }
    public Message CreateMessage(MessageType type, string nickname, string content)
    {
        if (nickname == null || content == null)
        {
            throw new ArgumentNullException("O apelido e o conteúdo não podem ser nulos.");
        }
        if (!Enum.IsDefined(typeof(MessageType), type))
        {
            throw new InvalidDataException("Tipo de mensagem inválido.");
        }
        byte[] nick = Encoding.UTF8.GetBytes(nickname);
        byte[] messageContent = Encoding.UTF8.GetBytes(content);
        int nickSize = nick.Length;
        int messageSize = messageContent.Length;
        if (nickSize > 64 || nickSize < 1)
        {
            throw new InvalidDataException("O apelido a ser enviado não pode ser vazio ou exceder 255 caracteres");
        }
        if (messageSize > 255)
        {
            throw new InvalidDataException("O conteúdo da mensagem é inválido");
        }

        return new Message
        {
            MessageType = type,
            NickSize = (byte)nickSize,
            Nick = nick,
            MessageSize = (byte)messageSize,
            MessageContent = messageContent
        };
    }
}