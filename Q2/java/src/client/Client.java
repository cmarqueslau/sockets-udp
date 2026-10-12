/*
   Data última atualização: 12/10/2026
   Estudante: Maria Eduarda Bambini
   Cliente UDP de upload de arquivos (envia partes de 1024 bytes e o SHA-1 final)
*/

package client;

import common.Message;
import static common.Message.*;
import common.Message.Ack;
import common.Message.Checksum;
import common.Message.Command;
import common.Message.Data;
import common.Message.Header;
import common.Message.Request;
import common.Message.Response;
import common.Message.Status;
import common.Prompt;
import common.Protocol;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
* Cliente de upload de arquivos via UDP. O UDP não garante a entrega; portanto, cada mensagem
* é enviada no modelo "pare e espere" (stop-and-wait): ela é retransmitida até que o servidor
* responda (ACK ou resposta) ou as tentativas se esgotem. 
*/
public class Client implements AutoCloseable {

    private static final int ACK_TIMEOUT_MS = 500;
    private static final int MAX_ATTEMPTS = 10;

    /** É esperada uma Response em vez de um ACK (números de sequência reais nunca são negativos). */
    private static final long NO_ACK = -1;

    private final DatagramSocket socket;
    private final Protocol protocol;
    private final SocketAddress server;
    private long retransmissions;

    public Client(SocketAddress server) throws SocketException {
        this.server = server;
        this.socket = new DatagramSocket();
        this.protocol = new Protocol(socket);
    }

    /* Faz o upload de um arquivo. */
    public boolean upload(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new NoSuchFileException(file.toString());
        }

        retransmissions = 0;
        drainSocket();

        sendForAck(new Request(Command.ADD_FILE), REQUEST_SEQ);
        sendForAck(new Header(file.getFileName().toString(), Files.size(file)), HEADER_SEQ);

        MessageDigest sha1 = Protocol.newSha1();

        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            byte[] chunk = new byte[MAX_PAYLOAD_SIZE];
            long seq = FIRST_DATA_SEQ;

            // readNBytes preenche o buffer, a menos que o arquivo termine. Cada parte tem exatamente
            // 1024 bytes, exceto possivelmente a última.
            for (int read; (read = in.readNBytes(chunk, 0, chunk.length)) > 0; seq++) {
                sha1.update(chunk, 0, read);
                sendForAck(new Data(seq, Arrays.copyOf(chunk, read)), seq);
            }
        }

        return sendForStatus(new Checksum(sha1.digest())) == Status.SUCCESS;
    }

    public long retransmissions() {
        return retransmissions;
    }

    @Override
    public void close() {
        socket.close();
    }

    /* Descarta datagramas atrasados de um upload anterior que ainda estejam no socket. */
    private void drainSocket() throws IOException {
        socket.setSoTimeout(1);

        while (true) {
            try {
                protocol.receive();
            } catch (SocketTimeoutException e) {
                return;
            } catch (ProtocolException e) {
                // datagrama inválido
            }
        }
    }

    private Status sendForStatus(Message message) throws IOException {
        return ((Response) sendAndWait(message, NO_ACK)).status();
    }

    private void sendForAck(Message message, long seq) throws IOException {
        if (!(sendAndWait(message, seq) instanceof Ack)) {
            throw new IOException("server aborted the upload");
        }
    }

    /* Envia uma mensagem e aguarda a resposta, retransmitindo em caso de timeout. */
    private Message sendAndWait(Message message, long ackSeq) throws IOException {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (attempt > 1) {
                retransmissions++;
            }

            protocol.send(message, server);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ACK_TIMEOUT_MS);

            // Lê até que a resposta chegue ou o timeout expire; respostas
            // vazias ou duplicadas são ignoradas sem reiniciar o temporizador.
            for (long left; (left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())) > 0; ) {
                socket.setSoTimeout((int) left);

                try {
                    Protocol.Received received = protocol.receive();

                    if (server.equals(received.source()) && answers(received.message(), ackSeq)) {
                        return received.message();
                    }
                } catch (SocketTimeoutException e) {
                    break;
                } catch (ProtocolException e) {
                }
            }
        }

        throw new SocketTimeoutException("no answer from the server after " + MAX_ATTEMPTS + " attempts");
    }

    /* Indica se uma mensagem recebida responde àquela que está em andamento. */
    private static boolean answers(Message reply, long ackSeq) {
        return switch (reply) {
            case Ack a -> a.seq() == ackSeq;
            case Response r -> ackSeq == NO_ACK || r.status() == Status.ERROR;
            default -> false;
        };
    }

    /* Envia um arquivo e mostra o resultado; um erro neste envio não encerra o programa. */
    private static void uploadAndReport(Client client, Path file) {
        try {
            long start = System.nanoTime();
            boolean stored = client.upload(file);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            System.out.println((stored ? "OK" : "FAILED (checksum rejected)") + " - " + file.getFileName()
                    + ", " + elapsedMs + " ms, " + client.retransmissions() + " retransmissions");
        } catch (IOException e) {
            System.err.println("upload failed: " + e);
        }
    }

    /* Pergunta o IP e a porta do servidor até receber um endereço válido; null se a entrada acabar. */
    private static InetSocketAddress askServerAddress(Scanner input) {
        while (true) {
            String host = Prompt.ask(input, "IP do servidor", "127.0.0.1");
            String port = Prompt.ask(input, "Porta", "5000");

            if (host == null || port == null) {
                return null;
            }

            try {
                InetSocketAddress address = new InetSocketAddress(host, Integer.parseInt(port));

                if (!address.isUnresolved()) {
                    return address;
                }

                System.out.println("Servidor não encontrado: " + host);
            } catch (IllegalArgumentException e) { // número inválido ou fora da faixa 0-65535
                System.out.println("Porta inválida: " + port);
            }
        }
    }

    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        InetSocketAddress server = askServerAddress(input);

        if (server == null) {
            return;
        }

        try (Client client = new Client(server)) {
            System.out.println("Enviando para " + server + ".");

            while (true) {
                String file = Prompt.ask(input, "Arquivo para enviar (ou 'sair')", null);

                if (file == null || file.equalsIgnoreCase("sair")) {
                    break;
                }

                if (!file.isEmpty()) {
                    uploadAndReport(client, Paths.get(file));
                }
            }
        } catch (SocketException e) {
            System.err.println("Não foi possível abrir o socket: " + e);
            System.exit(1);
        }
    }
}