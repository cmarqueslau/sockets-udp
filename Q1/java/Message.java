/**
 * Representa uma mensagem ja decodificada do chat.
 * Guarda apenas os dados: tipo, apelido de quem enviou e o texto.
 */
public class Message {

    public int type;
    public String nickname;
    public String text;

    public Message(int type, String nickname, String text) {
        this.type = type;
        this.nickname = nickname;
        this.text = text;
    }
}