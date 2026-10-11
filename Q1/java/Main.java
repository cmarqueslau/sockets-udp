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

    public static final int ECHO_WAIT_TIME = 10000;

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
        Style.currentPrompt = Style.BOLD + prompt + Style.RESET;
        System.out.print(Style.currentPrompt);
        System.out.flush();

        if (!scanner.hasNextLine()) {
            Style.currentPrompt = "";
            return null;
        }

        String line = scanner.nextLine();
        Style.currentPrompt = "";
        return line;
    }

    /**
     * Mostra o menu e pergunta o tipo de mensagem ao usuário.
     * Retorna -1 se a entrada foi encerrada e -2 se o valor for inválido.
     */
    private static int getType() {
        String input = readLine("Tipo: ");

        if (input == null) {
            return -1;
        }

        input = input.trim();

        if (input.equalsIgnoreCase("EXIT")) {
            return 0;
        }

        try {
            return Integer.parseInt(input);
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

            Style.showInfo("Cliente iniciado");
            Style.printLine(Style.GRAY + Style.SEPARATOR + Style.RESET);

            String typedNickname = readLine("Seu apelido: ");
            if (typedNickname == null) {
                return;
            }
            nickname = typedNickname.trim();

            int nicknameSize = nickname.getBytes(StandardCharsets.UTF_8).length;
            if (nicknameSize < Protocol.NICKNAME_SIZE_MIN || nicknameSize > Protocol.NICKNAME_SIZE_MAX) {
                Style.showError("Apelido deve ter de 1 a 64 bytes.");
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
            Style.printLine(Style.GRAY + Style.SEPARATOR + Style.RESET);

            if (destPortText == null) {
                return;
            }

            int destPort = Integer.parseInt(destPortText.trim());

            socket = new DatagramSocket(localPort);

            Thread receiverThread = new Thread(new Receptor(socket, nickname));
            receiverThread.start();

            Style.showInfo("Cliente iniciado na porta " + localPort);
            Style.showInfo("Tipos de mensagens permitidos: 1 - Normal | 2 - Emoji | 3 - URL | 4 - ECHO | EXIT");

            while (active) {
                int type = getType();

                if (type == 0 || type == -1) {
                    active = false;
                } else if (type < Protocol.TYPE_NORMAL || type > Protocol.TYPE_ECHO) {
                    Style.showError("Tipo inválido. Use 1, 2, 3, 4 ou EXIT.");
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
                        Style.showWarning("Mensagem vazia, nada foi enviado.");
                    } else if (textSize > Protocol.MESSAGE_SIZE_MAX) {
                        Style.showError("Mensagem muito grande (máximo 255 bytes).");
                    } else if (type == Protocol.TYPE_URL && !isURLValid(text)) {
                        Style.showError("URL inválida (ex.: https://exemplo.com).");
                    } else {
                        byte[] data = Protocol.montarMensagem(type, nickname, text);

                        if (type == Protocol.TYPE_ECHO) {
                            waitingEcho = true;
                        }

                        sendPacket(data, destIp, destPort);

                        if (type == Protocol.TYPE_ECHO) {
                            Style.showSent(nickname, type, text);
                            Style.showInfo("ECHO enviado, aguardando resposta...");
                            Thread.sleep(ECHO_WAIT_TIME);
                            if (waitingEcho) {
                                waitingEcho = false;
                                Style.showWarning("ECHO sem resposta: usuário inativo.");
                            }
                        } else {
                            Style.showSent(nickname, type, text);
                        }
                    }
                }
            }

            Style.showInfo("Chat encerrado.");

        } catch (NumberFormatException e) {
            Style.showError("Porta inválida: " + e.getMessage());
        } catch (SocketException e) {
            Style.showError("Socket: " + e.getMessage());
        } catch (IOException e) {
            Style.showError("IO: " + e.getMessage());
        } catch (InterruptedException e) {
            Style.showError("Interrompido: " + e.getMessage());
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