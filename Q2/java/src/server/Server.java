/*
   Data última atualização: 11/10/2026
   Estudante: Maria Eduarda Bambini
   Servidor UDP de upload de arquivos (recebe partes de 1024 bytes e confere o SHA-1)
*/

package server;

import common.Message;
import static common.Message.*;
import common.Message.Ack;
import common.Message.Checksum;
import common.Message.Data;
import common.Message.Header;
import common.Message.Request;
import common.Message.Response;
import common.Message.Status;
import common.Protocol;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.ProtocolException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Servidor de upload de arquivos via UDP. Uma thread lê datagramas e mantém um {@link Upload} por endereço
 * de cliente, permitindo que vários clientes realizem uploads simultaneamente. 
 */
public class Server implements AutoCloseable {

    private static final int DEFAULT_PORT = 9000;
    private static final String DEFAULT_DIRECTORY = "uploads";
    private static final long MAX_FILE_SIZE = 1L << 30; // 1 GiB
    private static final long IDLE_TIMEOUT_MS = 15_000;
    private static final long FINISHED_LINGER_MS = 5_000;

    private final DatagramSocket socket;
    private final Protocol protocol;
    private final Path storageDir;
    private final Map<SocketAddress, Upload> uploads = new HashMap<>();

    /**
     * Vincula o socket e cria a pasta de armazenamento.
    */
    public Server(int port, Path storageDir, double dropRate) throws IOException {
        this.storageDir = storageDir.toAbsolutePath().normalize();
        Files.createDirectories(this.storageDir);

        this.socket = new DatagramSocket(port);
        this.socket.setSoTimeout(1000);
        this.protocol = new Protocol(socket, dropRate);
    }

    public void run() throws IOException {
        try {
            while (true) {
                try {
                    Protocol.Received received = protocol.receive();
                    handle(received.message(), received.source());
                } catch (SocketTimeoutException e) {
                } catch (ProtocolException e) {
                    // datagrama inválido ou upload rejeitado (o cliente já foi notificado): segue atendendo
                } catch (SocketException e) {
                    if (socket.isClosed()) {
                        return;
                    }
                    throw e;
                }

                expireUploads();
            }
        } finally {
            uploads.values().forEach(Upload::discard); // excluir arquivos .part incompletos
        }
    }

    /** Interrompe o loop de recebimento. */
    @Override
    public void close() {
        socket.close();
    }

    private void handle(Message message, SocketAddress from) throws IOException {
        Upload upload = uploads.get(from);

        if (upload != null) {
            upload.lastSeen = System.nanoTime();
        }

        switch (message) {
            case Request r -> onRequest(from);
            case Header h -> onHeader(h, from);
            case Data d -> onData(d, from);
            case Checksum c -> onChecksum(c, from);
            default -> { }
        }
    }

    /**
     * Inicia um novo upload e responde com SUCESSO. Uma requisição repetida (resposta perdida)
     * mantém o upload; uma requisição em qualquer outro estado significa que o cliente recomeçou. 
    */
    private void onRequest(SocketAddress from) throws IOException {
        Upload current = uploads.get(from);

        if (current == null || current.started || current.result != null) {
            if (current != null) {
                current.discard();
            }

            uploads.put(from, new Upload());
        }

        send(from, new Response(Status.SUCCESS));
    }

    /* Cabeçalho: valida nome e tamanho, abre o arquivo temporário e responde com ACK(0). */
    private void onHeader(Header header, SocketAddress from) throws IOException {
        Upload upload = uploads.get(from);

        if (upload == null) {
            send(from, new Response(Status.ERROR));
            return;
        }

        if (upload.result != null) {
            return;
        }

        if (upload.started) { // cabeçalho repetido se ACK foi perdido
            if (header.fileName().equals(upload.fileName) && header.fileSize() == upload.size) {
                send(from, new Ack(HEADER_SEQ));
            } else {
                reject(from, upload, "conflicting Header");
            }
            return;
        }

        if (!isSafeFileName(header.fileName())) {
            reject(from, upload, "unsafe file name '" + header.fileName() + "'");
            return;
        }

        if (header.fileSize() > MAX_FILE_SIZE) {
            reject(from, upload, "file too large: " + header.fileSize() + " bytes");
            return;
        }

        try {
            upload.open(storageDir, header.fileName(), header.fileSize());
        } catch (IOException e) {
            reject(from, upload, "cannot create file: " + e.getMessage());
            return;
        }

        send(from, new Ack(HEADER_SEQ));
    }

    /**
     * Data: armazena o pacote se for o esperado e responde com ACK(seq). Um pacote que já
     * foi armazenado apenas tem seu recebimento confirmado novamente; um pacote que está
     * à frente do esperado é ignorado (o cliente retransmitirá primeiro o pacote que falta). 
    */
    private void onData(Data data, SocketAddress from) throws IOException {
        Upload upload = uploads.get(from);

        if (upload == null) {
            send(from, new Response(Status.ERROR));
            return;
        }

        if (!upload.started || upload.result != null) {
            return;
        }

        if (data.seq() == upload.nextSeq) {
            if (upload.received + data.payload().length > upload.size) {
                reject(from, upload, "more data than announced");
                return;
            }

            try {
                upload.out.write(data.payload());
            } catch (IOException e) {
                reject(from, upload, "write failed: " + e.getMessage());
                return;
            }

            upload.sha1.update(data.payload()); // a mensagem é gerada à medida que os dados chegam
            upload.received += data.payload().length;
            upload.nextSeq++;
            send(from, new Ack(data.seq()));
        } else if (data.seq() >= FIRST_DATA_SEQ && data.seq() < upload.nextSeq) {
            send(from, new Ack(data.seq())); // ack foi perdido, sendo duplicado
        }
    }

    /* Checksum: verifica o arquivo e responde SUCCESS ou ERROR. */
    private void onChecksum(Checksum checksum, SocketAddress from) throws IOException {
        Upload upload = uploads.get(from);

        if (upload == null || (!upload.started && upload.result == null)) {
            send(from, new Response(Status.ERROR));
            return;
        }

        if (upload.result == null) {
            try {
                finish(upload, checksum.hash());
            } catch (ProtocolException e) {
                reject(from, upload, e.getMessage());
            } catch (IOException e) {
                reject(from, upload, "could not store '" + upload.fileName + "': " + e.getMessage());
            }
        }

        send(from, new Response(upload.result));
    }

    /**
     * Fecha o arquivo temporário e compara o SHA-1 calculado aqui com o enviado pelo
     * cliente. Apenas um arquivo completo com um hash correspondente recebe seu nome final; 
     * qualquer outra coisa é excluída, de modo que um arquivo corrompido nunca aparece na pasta de armazenamento. 
    */
    private void finish(Upload upload, byte[] expectedHash) throws IOException {
        upload.out.close();
        byte[] actualHash = upload.sha1.digest();

        if (upload.received != upload.size || !MessageDigest.isEqual(actualHash, expectedHash)) {
            throw new ProtocolException("discarded '" + upload.fileName + "': incomplete file or checksum mismatch");
        }

        Files.move(upload.tempFile, upload.target, StandardCopyOption.REPLACE_EXISTING);
        upload.tempFile = null;
        upload.result = Status.SUCCESS;
    }

    /* Aceita apenas um nome de arquivo simples que é armazenado diretamente na pasta de armazenamento, bloqueando diretórios ("../x", "a/b", caminhos absolutos, ".." e "."). */
    private boolean isSafeFileName(String name) {
        try {
            return storageDir.equals(storageDir.resolve(name).normalize().getParent());
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /* Interrompe um upload, descarta o pacote e notifica o cliente com Response(ERROR). */
    private void reject(SocketAddress from, Upload upload, String reason) throws IOException {
        upload.discard();
        uploads.remove(from);
        send(from, new Response(Status.ERROR));
        throw new ProtocolException("rejected upload from " + from + ": " + reason);
    }

    /** Descarta uploads cujo tempo de espera (time-out) do cliente expirou. */
    private void expireUploads() {
        uploads.values().removeIf(upload -> {
            long idleMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - upload.lastSeen);
            boolean expired = idleMs > (upload.result != null ? FINISHED_LINGER_MS : IDLE_TIMEOUT_MS);

            if (expired) {
                upload.discard();
            }

            return expired;
        });
    }

    /** Envia uma mensagem, lançando uma exceção caso o socket falhe. */
    private void send(SocketAddress to, Message message) throws IOException {
        protocol.send(message, to);
    }
    
    /**
     * Estado do upload de um cliente. Os dados vão para um arquivo temporário ".part" e só recebem
     * o nome final depois que o checksum for verificado.
     */
    private static final class Upload {
        boolean started;
        Status result;
        String fileName;
        long size;
        long received;
        long nextSeq = FIRST_DATA_SEQ;
        Path tempFile;
        Path target;
        OutputStream out;
        final MessageDigest sha1 = Protocol.newSha1();
        long lastSeen = System.nanoTime();

        /** Cria o arquivo temporário (nome único, para que nomes iguais de dois clientes nunca entrem em conflito). */
        void open(Path dir, String name, long fileSize) throws IOException {
            tempFile = Files.createTempFile(dir, "upload-", ".part");
            out = new BufferedOutputStream(Files.newOutputStream(tempFile), 64 * 1024);
            target = dir.resolve(name);
            fileName = name;
            size = fileSize;
            started = true;
        }

        /** Fecha e exclui o arquivo temporário, se houver. Pode ser chamado mais de uma vez com segurança. */
        void discard() {
            try {
                if (out != null) {
                    out.close();
                }
                if (tempFile != null) {
                    Files.deleteIfExists(tempFile);
                }
            } catch (IOException ignored) {
                // Limpeza em melhor esforço.
            }

            tempFile = null;
        }
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        Path directory = Paths.get(args.length > 1 ? args[1] : DEFAULT_DIRECTORY);
        double dropRate = args.length > 2 ? Double.parseDouble(args[2]) : 0.0;

        Server server = new Server(port, directory, dropRate);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.run();
    }
}