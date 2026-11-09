package br.com.gestaosaude.util;

/**
 * Validação e formatação de CPF.
 */
public final class CpfUtils {

    private CpfUtils() {
    }

    public static String somenteDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    /** Valida tamanho, sequências repetidas e os dois dígitos verificadores. */
    public static boolean isValido(String cpf) {
        String d = somenteDigitos(cpf);
        if (d.length() != 11 || d.chars().distinct().count() == 1) {
            return false;
        }
        return digitoVerificador(d, 9) == d.charAt(9) - '0'
                && digitoVerificador(d, 10) == d.charAt(10) - '0';
    }

    private static int digitoVerificador(String digitos, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (digitos.charAt(i) - '0') * (quantidade + 1 - i);
        }
        return (soma * 10) % 11 % 10;
    }

    /** Formata para {@code 000.000.000-00}. Se não tiver 11 dígitos, devolve o valor original aparado. */
    public static String formatar(String cpf) {
        String d = somenteDigitos(cpf);
        if (d.length() != 11) {
            return cpf == null ? null : cpf.trim();
        }
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
    }
}
