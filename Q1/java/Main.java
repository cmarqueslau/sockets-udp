import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Chat P2P usando UDP.
 */
public class Main {

    public static final int ECHO_WAIT_TIME = 3000;

    // Flags compartilhadas com a thread Receptor.
    public static volatile boolean active = true;
    public static volatile boolean waitingEcho = false;

    private static DatagramSocket socket;
    private static String nickname;

    private static Scanner scanner = new Scanner(System.in);

    /**
     * Mostra o texto de pergunta e lê uma linha digitada pelo usuário.
     */
    private static String readLine(String prompt) {
        System.out.print(prompt);

        if (!scanner.hasNextLine()) {
            return null;
        }

        return scanner.nextLine();
    }

    /**
     * Mostra o menu e pergunta o tipo de mensagem ao usuário.
     * Retorna -1 se a entrada foi encerrada e -2 se o valor for inválido.
     */
    private static int getType() {
        System.out.println();
        System.out.println("Tipo da mensagem:");
        System.out.println("  1) Normal");
        System.out.println("  2) Emoji");
        System.out.println("  3) URL");
        System.out.println("  4) ECHO (verifica se o outro esta ativo)");
        System.out.println("  0) Sair");
        System.out.println("\n");
        String input = readLine("Escolha: ");

        if (input == null) {
            return -1;
        }

        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            return -2;
        }
    }

    /**
     * Verifica se o texto é uma URL válida.
     */
    private static boolean isURLValid(String text) {
        try {
            new URI(text).toURL();
            return true;
        } catch (URISyntaxException e) {
            return false;
        } catch (MalformedURLException e) {
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Envia um pacote UDP usando o socket do peer.
     */
    private static void sendPacket(byte[] data, InetAddress ip, int port) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, ip, port);
        socket.send(packet);
    }

    public static void main(String[] args) {
        try {

            String typedNickname = readLine("Seu apelido (1 a 64 bytes): ");
            if (typedNickname == null) {
                return;
            }
            nickname = typedNickname.trim();

            int nicknameSize = nickname.getBytes(StandardCharsets.UTF_8).length;
            if (nicknameSize < Protocol.NICKNAME_SIZE_MIN || nicknameSize > Protocol.NICKNAME_SIZE_MAX) {
                System.out.println("[Erro] Apelido deve ter de 1 a 64 bytes.");
                return;
            }

            String localPortText = readLine("Porta local: ");
            if (localPortText == null) {
                return;
            }
            int localPort = Integer.parseInt(localPortText.trim());

            String destIpText = readLine("IP destino: ");
            if (destIpText == null) {
                return;
            }
            InetAddress destIp = InetAddress.getByName(destIpText.trim());

            String destPortText = readLine("Porta destino: ");
            if (destPortText == null) {
                return;
            }
            int destPort = Integer.parseInt(destPortText.trim());

            socket = new DatagramSocket(localPort);

            Thread receiverThread = new Thread(new Receptor(socket, nickname));
            receiverThread.start();

            System.out.println();
            System.out.println("[OK] Conectado como '" + nickname + "' (porta local " + localPort + ")");
            System.out.println("[OK] Enviando para " + destIp.getHostAddress() + ":" + destPort);

            while (active) {
                int type = getType();

                if (type == 0 || type == -1) {
                    active = false;
                } else if (type < Protocol.TYPE_NORMAL || type > Protocol.TYPE_ECHO) {
                    System.out.println("[Erro] Tipo inválido.");
                } else {
                    String text;

                    if (type == Protocol.TYPE_ECHO) {
                        text = "ECHO";
                    } else {
                        text = readLine("Mensagem: ");
                        if (text == null) {
                            active = false;
                            continue;
                        }
                    }

                    int textSize = text.getBytes(StandardCharsets.UTF_8).length;

                    if (textSize == 0) {
                        System.out.println("[Aviso] Mensagem vazia, nada foi enviado.");
                    } else if (textSize > Protocol.MESSAGE_SIZE_MAX) {
                        System.out.println("[Erro] Mensagem muito grande (maximo 255 bytes).");
                    } else if (type == Protocol.TYPE_URL && !isURLValid(text)) {
                        System.out.println("[Erro] URL inválida (ex.: https://exemplo.com).");
                    } else {
                        byte[] data = Protocol.montarMensagem(type, nickname, text);

                        if (type == Protocol.TYPE_ECHO) {
                            waitingEcho = true;
                        }

                        sendPacket(data, destIp, destPort);

                        if (type == Protocol.TYPE_ECHO) {
                            System.out.println("[ECHO] Enviado, aguardando resposta...");
                            Thread.sleep(ECHO_WAIT_TIME);
                            if (waitingEcho) {
                                waitingEcho = false;
                                System.out.println("[ECHO] Sem resposta: usuário inativo.");
                            }
                        } else {
                            System.out.println("[Enviado] " + nickname + ": " + text);
                        }
                    }
                }
            }

            System.out.println("Encerrando o chat ...");

        } catch (NumberFormatException e) {
            System.out.println("[Erro] Porta inválida: " + e.getMessage());
        } catch (SocketException e) {
            System.out.println("[Erro] Socket: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("[Erro] IO: " + e.getMessage());
        } catch (InterruptedException e) {
            System.out.println("[Erro] Interrompido: " + e.getMessage());
        } finally {
            active = false;
            /* Lança exceção para fechar a thread */
            if (socket != null) {
                socket.close();
            }
            scanner.close();
        }
    }
}