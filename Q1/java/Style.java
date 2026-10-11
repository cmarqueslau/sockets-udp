public class Style {

    public static final String RESET = "\033[0m";
    public static final String BOLD = "\033[1m";
    public static final String RED = "\033[31m";
    public static final String GREEN = "\033[32m";
    public static final String YELLOW = "\033[33m";
    public static final String CYAN = "\033[36m";
    public static final String GRAY = "\033[90m";
    public static final String SEPARATOR = "------------------------------------------------------------------";
    public static volatile String currentPrompt = "";

    public static synchronized void printLine(String line) {
        System.out.print("\r\033[2K");
        System.out.println(line);
        System.out.print(currentPrompt);
        System.out.flush();
    }

    public static void showInfo(String text) {
        printLine(CYAN + "[Peer] " + text + RESET);
    }

    public static void showWarning(String text) {
        printLine(YELLOW + "[Peer] Aviso: " + text + RESET);
    }

    public static void showError(String text) {
        printLine(RED + "[Peer] Erro: " + text + RESET);
    }

    public static void showSent(String nickname, int type, String text) {
        printLine(GREEN + "[Peer:" + nickname + "] " + type + ": " + text + RESET);
    }

    public static void showReceived(String nickname, int type, String text) {
        printLine(YELLOW + "[Peer:" + nickname + "] " + type + ": " + text + RESET);
    }

}