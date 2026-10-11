import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * Thread que fica recebendo pacotes em laço e tratando cada tipo de mensagem.
 * Usa o mesmo socket do peer, recebido pelo construtor.
 */
public class Receptor implements Runnable {

    private DatagramSocket socket;
    private String nickname;

    public Receptor(DatagramSocket socket, String nickname) {
        this.socket = socket;
        this.nickname = nickname;
    }

    @Override
    public void run() {
        while (Main.active) {

            byte[] buffer = new byte[1000];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            try {
                socket.receive(packet);
            } catch (IOException e) {
                if (!Main.active) {
                    break;
                }
                System.out.println("Erro ao receber: " + e.getMessage());
                continue;
            }

            Message message = Protocol.decode(packet.getData(), packet.getLength());

            if (message == null) {
                System.out.println("[Pacote invalido descartado]");
                continue;
            }

            curateMessage(message, packet);
        }
    }

    /**
     * Trata cada tipo de mensagem recebida.
     */
    private void curateMessage(Message message, DatagramPacket packet) {

        if (message.type == Protocol.TYPE_NORMAL) {
            System.out.println(message.nickname + ": " + message.text);

        } else if (message.type == Protocol.TYPE_EMOJI) {
            System.out.println(message.nickname + " [emoji]: " + message.text);

        } else if (message.type == Protocol.TYPE_URL) {
            System.out.println(message.nickname + " [link]: " + message.text);

        } else if (message.type == Protocol.TYPE_ECHO) {

            if (Main.waitingEcho && message.nickname.equals(nickname)) {
                Main.waitingEcho = false;
                System.out.println("[ECHO] Usuário está ativo.");
            } else {
                try {
                    byte[] copy = new byte[packet.getLength()];
                    for (int i = 0; i < packet.getLength(); i++) {
                        copy[i] = packet.getData()[i];
                    }
                    DatagramPacket reply = new DatagramPacket(
                            copy, copy.length, packet.getAddress(), packet.getPort());
                    socket.send(reply);
                    System.out.println("[ECHO] Recebido de " + message.nickname + " (respondido).");
                } catch (IOException e) {
                    System.out.println("Erro ao responder ECHO: " + e.getMessage());
                }
            }
        }
    }
}