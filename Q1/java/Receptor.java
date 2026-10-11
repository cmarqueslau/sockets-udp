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
                Style.showError("Erro ao receber: " + e.getMessage());
                continue;
            }

            Message message = Protocol.decode(packet.getData(), packet.getLength());

            if (message == null) {
                Style.showWarning("Pacote inválido descartado.");
                continue;
            }

            curateMessage(message, packet);
        }
    }

    /**
     * Trata cada tipo de mensagem recebida.
     */
    private void curateMessage(Message message, DatagramPacket packet) {

        if (message.type == Protocol.TYPE_ECHO) {

            if (Main.waitingEcho && message.nickname.equals(nickname)) {
                Main.waitingEcho = false;
                Style.showInfo("ECHO: usuário está ativo.");

            } else {
                Style.showReceived(message.nickname, message.type, message.text);

                try {
                    byte[] copy = new byte[packet.getLength()];
                    for (int i = 0; i < packet.getLength(); i++) {
                        copy[i] = packet.getData()[i];
                    }
                    DatagramPacket reply = new DatagramPacket(
                            copy, copy.length, packet.getAddress(), packet.getPort());
                    socket.send(reply);
                    Style.showInfo("ECHO respondido a " + message.nickname);
                } catch (IOException e) {
                    Style.showError("Erro ao responder ECHO: " + e.getMessage());
                }
            }
        } else {
            // Tipos 1 (normal), 2 (emoji) e 3 (URL): apenas exibe
            Style.showReceived(message.nickname, message.type, message.text);
        }
    }
}