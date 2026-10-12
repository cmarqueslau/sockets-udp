/*
   Data última atualização: 12/10/2026
   Estudante: Maria Eduarda Bambini
   Leitura de dados digitados no terminal (usada pelo servidor e pelo cliente)
*/

package common;

import java.util.Scanner;

/** Funções de apoio para perguntar valores ao usuário. */
public final class Prompt {

    private Prompt() {
    }

    public static String ask(Scanner input, String label, String defaultValue) {
        System.out.print(label + (defaultValue != null ? " [" + defaultValue + "]" : "") + ": ");

        if (!input.hasNextLine()) {
            return null;
        }

        String answer = input.nextLine().trim();
        return answer.isEmpty() && defaultValue != null ? defaultValue : answer;
    }
}