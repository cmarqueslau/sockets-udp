import java.nio.charset.StandardCharsets;

/**
 * 
 */
public class Protocol {

    public static final int TYPE_NORMAL = 1;
    public static final int TYPE_EMOJI = 2;
    public static final int TYPE_URL = 3;
    public static final int TYPE_ECHO = 4;

    public static final int NICKNAME_SIZE_MIN = 1;
    public static final int NICKNAME_SIZE_MAX = 64;
    public static final int MESSAGE_SIZE_MAX = 255;

    /**
     * Monta o vetor de bytes no formato do protocolo.
     */
    public static byte[] montarMensagem(int type, String nickname, String text) {
        byte[] nicknameBytes = nickname.getBytes(StandardCharsets.UTF_8);
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);

        // tipo + tam_apl + apelido + tam_msg + mensagem
        int total = 1 + 1 + nicknameBytes.length + 1 + textBytes.length;
        byte[] result = new byte[total];
        int pos = 0;

        result[pos] = (byte) type;
        pos = pos + 1;

        result[pos] = (byte) nicknameBytes.length;
        pos = pos + 1;

        for (int i = 0; i < nicknameBytes.length; i++) {
            result[pos] = nicknameBytes[i];
            pos = pos + 1;
        }

        result[pos] = (byte) textBytes.length;
        pos = pos + 1;

        for (int i = 0; i < textBytes.length; i++) {
            result[pos] = textBytes[i];
            pos = pos + 1;
        }

        return result;
    }

    /**
     * Decodifica um pacote recebido. Retorna null se for inválido.
     */
    public static Message decode(byte[] data, int packetSize) {
        // Minimo: tipo + tam_apl + 1 byte de apelido + tam_msg = 4 bytes
        if (packetSize < 4) {
            return null;
        }

        int pos = 0;

        // "& 0xFF" converte o byte (com sinal) para valor de 0 a 255
        int type = data[pos] & 0xFF;
        pos = pos + 1;

        int nicknameSize = data[pos] & 0xFF;
        pos = pos + 1;

        if (type < TYPE_NORMAL || type > TYPE_ECHO) {
            return null;
        }
        if (nicknameSize < NICKNAME_SIZE_MIN || nicknameSize > NICKNAME_SIZE_MAX) {
            return null;
        }
        // Precisa caber o apelido e o byte do tam_msg
        if (pos + nicknameSize + 1 > packetSize) {
            return null;
        }

        String nickname = new String(data, pos, nicknameSize, StandardCharsets.UTF_8);
        pos = pos + nicknameSize;

        int messageSize = data[pos] & 0xFF;
        pos = pos + 1;

        if (pos + messageSize > packetSize) {
            return null;
        }

        String text = new String(data, pos, messageSize, StandardCharsets.UTF_8);

        return new Message(type, nickname, text);
    }
}