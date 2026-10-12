/*
   Data última atualização: 11/10/2026
   Estudante: Maria Eduarda Bambini
   Definição do protocolo comum entre linguagens
*/

package common;

import static common.Message.*;
import common.Message.Ack;
import common.Message.Checksum;
import common.Message.Command;
import common.Message.Data;
import common.Message.Header;
import common.Message.Request;
import common.Message.Response;
import common.Message.Status;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ProtocolException;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
* Formato de transmissão binária do protocolo sobre um socket UDP (os números estão em big-endian,
* que é o formato utilizado por DataInput/DataOutput). Cada datagrama transporta exatamente uma mensagem. 
*/
public class Protocol {

    private static final int REQUEST = 0x01;
    private static final int RESPONSE = 0x02;
    private static final int HEADER = 0x03;
    private static final int DATA = 0x04;
    private static final int CHECKSUM = 0x05;
    private static final int ACK = 0x06;

    /** Maior datagrama do protocolo = Dados: tipo(1) + seq(4) + comprimento(2) + payload. */
    private static final int MAX_PACKET_SIZE = 1 + 4 + 2 + MAX_PAYLOAD_SIZE;

    /** Uma mensagem decodificada e o endereço do par que a enviou. */
    public record Received(Message message, SocketAddress source) { }

    private final DatagramSocket socket;
    
    public Protocol(DatagramSocket socket) {
        this.socket = socket;
    }

    /** Cria um resumo SHA-1. */
    public static MessageDigest newSha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    /* Aguarda um datagrama e o decodifica. */
    public Received receive() throws IOException {
        DatagramPacket packet = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
        socket.receive(packet);

        ByteArrayInputStream bytes = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());

        try {
            Message message = decode(new DataInputStream(bytes));

            if (bytes.available() > 0) {
                throw new ProtocolException("unexpected bytes after the message");
            }

            return new Received(message, packet.getSocketAddress());
        } catch (EOFException | IllegalArgumentException e) {
            throw new ProtocolException("malformed datagram (" + e + ")");
        }
    }

    /* Decodifica uma mensagem e a envia em um datagrama. */
    public void send(Message message, SocketAddress to) throws IOException {

        byte[] bytes = encode(message);
        socket.send(new DatagramPacket(bytes, bytes.length, to));
    }

    private static Message decode(DataInputStream in) throws IOException {
        int type = in.readUnsignedByte();

        return switch (type) {
            case REQUEST -> new Request(readEnum(in, Command.values()));
            case RESPONSE -> new Response(readEnum(in, Status.values()));
            case HEADER -> {
                int nameLength = in.readUnsignedByte();
                long fileSize = in.readLong();
                yield new Header(new String(readBytes(in, nameLength), StandardCharsets.UTF_8), fileSize);
            }
            case DATA -> {
                long seq = in.readInt() & 0xFFFFFFFFL; // unsigned 32 bits
                int length = in.readUnsignedShort();
                yield new Data(seq, readBytes(in, length));
            }
            case CHECKSUM -> new Checksum(readBytes(in, HASH_SIZE));
            case ACK -> new Ack(in.readInt() & 0xFFFFFFFFL);
            default -> throw new ProtocolException("unknown message type " + type);
        };
    }

    /* Traduz uma mensagem para um vetor de bytes. */
    private static byte[] encode(Message message) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);

        switch (message) {
            case Request r -> {
                out.writeByte(REQUEST);
                out.writeByte(r.command().ordinal() + 1);
            }
            case Response r -> {
                out.writeByte(RESPONSE);
                out.writeByte(r.status().ordinal() + 1);
            }
            case Header h -> {
                byte[] name = h.fileName().getBytes(StandardCharsets.UTF_8);
                out.writeByte(HEADER);
                out.writeByte(name.length);
                out.writeLong(h.fileSize());
                out.write(name);
            }
            case Data d -> {
                out.writeByte(DATA);
                out.writeInt((int) d.seq()); // os 32 bits menos significativos armazenam o valor sem sinal
                out.writeShort(d.payload().length);
                out.write(d.payload());
            }
            case Checksum c -> {
                out.writeByte(CHECKSUM);
                out.write(c.hash());
            }
            case Ack a -> {
                out.writeByte(ACK);
                out.writeInt((int) a.seq());
            }
        }

        return bytes.toByteArray();
    }

    /** Lê e mapeia um código de um byte para um valor enum. (code 1 = primeiro enum). */
    private static <E extends Enum<E>> E readEnum(DataInputStream in, E[] values) throws IOException {
        int code = in.readUnsignedByte();

        if (code < 1 || code > values.length) {
            throw new ProtocolException("unknown code " + code);
        }

        return values[code - 1];
    }

    /** Lê exatamente {@code length} bytes (lança EOFException se o datagrama for muito curto). */
    private static byte[] readBytes(DataInputStream in, int length) throws IOException {
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
    }
}