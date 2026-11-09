package br.com.gestaosaude.view;

import java.io.PrintStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * Leitura validada de dados do console. Repete a pergunta até receber um valor válido.
 */
final class ConsoleIO {

    static final DateTimeFormatter FORMATO_DATA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    /** Lançada quando a entrada padrão termina (Ctrl+D / fim de arquivo). */
    static final class EntradaEncerradaException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private final Scanner scanner;
    private final PrintStream out;

    ConsoleIO(Scanner scanner, PrintStream out) {
        this.scanner = scanner;
        this.out = out;
    }

    String lerLinha(String rotulo) {
        out.print(rotulo);
        try {
            return scanner.nextLine().trim();
        } catch (NoSuchElementException | IllegalStateException e) {
            throw new EntradaEncerradaException();
        }
    }

    String lerTexto(String rotulo) {
        while (true) {
            String valor = lerLinha(rotulo);
            if (!valor.isEmpty()) {
                return valor;
            }
            out.println("  ! Campo obrigatório.");
        }
    }

    /** Retorna {@code null} se o usuário deixar em branco. */
    String lerTextoOpcional(String rotulo) {
        String valor = lerLinha(rotulo);
        return valor.isEmpty() ? null : valor;
    }

    /** Para edição: em branco mantém o valor atual. */
    String lerTextoOuManter(String rotulo, String atual) {
        String valor = lerLinha(rotulo + " [" + (atual == null ? "" : atual) + "]: ");
        return valor.isEmpty() ? atual : valor;
    }

    int lerInt(String rotulo, int min, int max) {
        while (true) {
            String valor = lerLinha(rotulo);
            try {
                int n = Integer.parseInt(valor);
                if (n >= min && n <= max) {
                    return n;
                }
            } catch (NumberFormatException ignored) {
                // cai na mensagem abaixo
            }
            out.printf("  ! Informe um número inteiro entre %d e %d.%n", min, max);
        }
    }

    /** Aceita vírgula ou ponto como separador decimal. */
    double lerDouble(String rotulo, double min, double max) {
        while (true) {
            String valor = lerLinha(rotulo).replace(',', '.');
            try {
                double n = Double.parseDouble(valor);
                if (n >= min && n <= max) {
                    return n;
                }
            } catch (NumberFormatException ignored) {
                // cai na mensagem abaixo
            }
            out.printf("  ! Informe um número entre %s e %s.%n", min, max);
        }
    }

    LocalDate lerData(String rotulo) {
        while (true) {
            LocalDate data = converterData(lerLinha(rotulo));
            if (data != null) {
                return data;
            }
            out.println("  ! Data inválida. Use o formato dd/mm/aaaa.");
        }
    }

    LocalDate lerDataOuManter(String rotulo, LocalDate atual) {
        while (true) {
            String valor = lerLinha(rotulo + " [" + (atual == null ? "" : FORMATO_DATA.format(atual)) + "]: ");
            if (valor.isEmpty()) {
                return atual;
            }
            LocalDate data = converterData(valor);
            if (data != null) {
                return data;
            }
            out.println("  ! Data inválida. Use o formato dd/mm/aaaa.");
        }
    }

    boolean confirmar(String pergunta) {
        while (true) {
            String r = lerLinha(pergunta + " (s/n): ").toLowerCase();
            if (r.equals("s") || r.equals("sim")) {
                return true;
            }
            if (r.equals("n") || r.equals("nao") || r.equals("não")) {
                return false;
            }
        }
    }

    void pausar() {
        lerLinha("\nPressione ENTER para continuar...");
    }

    private static LocalDate converterData(String valor) {
        try {
            return LocalDate.parse(valor, FORMATO_DATA);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
