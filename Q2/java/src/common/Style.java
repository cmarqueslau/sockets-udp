/*
   Data última atualização: 12/10/2026
   Estudante: Maria Eduarda Bambini
   Arquivo auxiliar para estilização do terminal.
*/

package common;

/** Cores ANSI e funções de apoio para a saída colorida no terminal. */
public final class Style {

    public static final String RESET = "\033[0m";
    public static final String BOLD = "\033[1m";
    public static final String RED = "\033[31m";
    public static final String GREEN = "\033[32m";
    public static final String YELLOW = "\033[33m";
    public static final String CYAN = "\033[36m";
    public static final String GRAY = "\033[90m";
    public static final String SEPARATOR = "------------------------------------------------------------------";
    public static volatile String currentPrompt = "";

    private Style() {
    }

    public static synchronized void printLine(String line) {
        System.out.print("\r\033[2K");
        System.out.println(line);
        System.out.print(currentPrompt);
        System.out.flush();
    }

    public static void showInfo(String text) {
        printLine(CYAN + text + RESET);
    }

    public static void showSuccess(String text) {
        printLine(GREEN + text + RESET);
    }

    public static void showWarning(String text) {
        printLine(YELLOW + "Aviso: " + text + RESET);
    }

    public static void showError(String text) {
        printLine(RED + "Erro: " + text + RESET);
    }
}