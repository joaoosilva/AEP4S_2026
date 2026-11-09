package br.com.gestaosaude.exception;

/**
 * Violação de regra de negócio ou de validação de dados (ex.: CPF inválido,
 * paciente já cadastrado, transição de status inválida).
 * <p>
 * A mensagem é sempre amigável e pode ser exibida diretamente ao usuário.
 */
public class NegocioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NegocioException(String message) {
        super(message);
    }

    public NegocioException(String message, Throwable cause) {
        super(message, cause);
    }
}
