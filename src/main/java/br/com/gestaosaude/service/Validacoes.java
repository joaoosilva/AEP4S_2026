package br.com.gestaosaude.service;

import br.com.gestaosaude.exception.NegocioException;
import br.com.gestaosaude.util.CpfUtils;

/**
 * Validações de entrada reutilizadas pelos serviços.
 */
final class Validacoes {

    private Validacoes() {
    }

    static String textoObrigatorio(String valor, String campo, int tamanhoMaximo) {
        if (valor == null || valor.isBlank()) {
            throw new NegocioException(campo + " é obrigatório.");
        }
        String v = valor.trim().replaceAll("\\s+", " ");
        if (v.length() > tamanhoMaximo) {
            throw new NegocioException(campo + " deve ter no máximo " + tamanhoMaximo + " caracteres.");
        }
        return v;
    }

    static String textoOpcional(String valor, String campo, int tamanhoMaximo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return textoObrigatorio(valor, campo, tamanhoMaximo);
    }

    /** Valida e devolve o CPF no formato 000.000.000-00. */
    static String cpf(String cpf) {
        if (!CpfUtils.isValido(cpf)) {
            throw new NegocioException("CPF inválido: " + (cpf == null ? "" : cpf.trim()));
        }
        return CpfUtils.formatar(cpf);
    }
}
