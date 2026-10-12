/*
   Data última atualização: 12/10/2026
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
import common.Prompt;
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
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Servidor de upload de arquivos via UDP. Uma thread lê datagramas e mantém um {@link Upload} por endereço
 * de cliente, permitindo que vários clientes realizem uploads simultaneamente. 
 */
public class Server implements AutoCloseable {

    private static final int DEFAULT_PORT = 5000;
    private static final String DEFAULT_DIRECTORY = "uploads";
    private static final long MAX_FILE_SIZE = 1L << 30; // 1 GiB
    private static final long IDLE_TIMEOUT_MS = 15_000;
    private static final long FINISHED_LINGER_MS = 5_000;

    private final DatagramSocket socket;
    private final Protocol protocol;
    private final Path storageDir;
    private final Map<SocketAddress, Upload> uploads = new HashMap<>();

    /* Vincula o socket e cria a pasta de armazenamento. */
    public Server(int port, Path storageDir) throws IOException {
        this.storageDir = storageDir.toAbsolutePath().normalize();
        Files.createDirectories(this.storageDir);

        this.socket = new DatagramSocket(port);
        this.socket.setSoTimeout(1000);
        this.protocol = new Protocol(socket);
    }

    /** Loop de recebimento: executa até que {@link #close()} seja chamado. */
    public void run() throws IOException {
        log("listening on UDP port " + socket.getLocalPort() + ", storing files in " + storageDir);

        try {
            while (true) {
                try {
                    Protocol.Received received = protocol.receive();
                    handle(received.message(), received.source());
                } catch (SocketTimeoutException e) {
                } catch (ProtocolException e) {
                    // datagrama inválido ou upload rejeitado
                    log(e.getMessage());
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
    * Request: inicia um novo upload e responde com ACK(0). Um Request repetido (resposta perdida)
    * mantém o upload; um Request em qualquer outro estado significa que o cliente recomeçou.
    */
    private void onRequest(SocketAddress from) {
        Upload current = uploads.get(from);

        if (current == null || current.started || current.result != null) {
            if (current != null) {
                current.discard();
            }

            uploads.put(from, new Upload());
        }

        send(from, new Ack(REQUEST_SEQ));
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

        log("receiving '" + header.fileName() + "' (" + header.fileSize() + " bytes) from " + from);
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
    private void onChecksum(Checksum checksum, SocketAddress from) {
        Upload upload = uploads.get(from);

        if (upload == null || (!upload.started && upload.result == null)) {
            send(from, new Response(Status.ERROR));
            return;
        }

        if (upload.result == null) {
            finish(upload, checksum.hash());
        }

        send(from, new Response(upload.result));
    }

    /**
     * Fecha o arquivo temporário e compara o SHA-1 calculado aqui com o enviado pelo
     * cliente. Apenas um arquivo completo com um hash correspondente recebe seu nome final; 
     * qualquer outra coisa é excluída, de modo que um arquivo corrompido nunca aparece na pasta de armazenamento. 
     */
    private void finish(Upload upload, byte[] expectedHash) {
        boolean verified = false;
        byte[] actualHash = null;

        try {
            upload.out.close();
            actualHash = upload.sha1.digest();

            if (upload.received == upload.size && MessageDigest.isEqual(actualHash, expectedHash)) {
                Files.move(upload.tempFile, upload.target, StandardCopyOption.REPLACE_EXISTING);
                upload.tempFile = null; // moved: nothing left to delete
                verified = true;
            }
        } catch (IOException e) {
            log("could not store '" + upload.fileName + "': " + e.getMessage());
        }

        if (verified) {
            log("stored '" + upload.fileName + "' (SHA-1 " + HexFormat.of().formatHex(actualHash) + ")");
        } else {
            log("discarded '" + upload.fileName + "': incomplete file or checksum mismatch");
            upload.discard();
        }

        upload.result = verified ? Status.SUCCESS : Status.ERROR;
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

    /** Envia uma mensagem, registrando no log (em vez de lançar exceção) caso o socket falhe. */
    private void send(SocketAddress to, Message message) {
        try {
            protocol.send(message, to);
        } catch (IOException e) {
            log("could not send to " + to + ": " + e.getMessage());
        }
    }

    private static void log(String text) {
        System.out.println("[" + LocalTime.now().withNano(0) + "] " + text);
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
        Scanner input = new Scanner(System.in);
        Server server;

        while (true) {
            String port = Prompt.ask(input, "Porta", null);
            String directory = Prompt.ask(input, "Pasta de armazenamento", null);

            if (port == null || directory == null) {
                return;
            }

            if (port.isEmpty()) {
                port = String.valueOf(DEFAULT_PORT);
            }
            if (directory.isEmpty()) {
                directory = DEFAULT_DIRECTORY;
            }

            try {
                server = new Server(Integer.parseInt(port), Paths.get(directory));
                break;
            } catch (IllegalArgumentException e) {
                System.out.println("Porta ou pasta inválida."); // número inválido, porta fora da faixa ou nome de pasta inválido
            } catch (IOException e) {
                System.out.println("Não foi possível iniciar o servidor: " + e.getMessage()); // ex.: porta em uso
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.run();
    }
}