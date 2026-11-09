package br.com.gestaosaude.exception;

/**
 * Exceção não checada da camada de persistência.
 * <p>
 * Encapsula {@link java.sql.SQLException} (e demais falhas de infraestrutura)
 * para que as camadas de domínio/serviço não dependam de tipos do JDBC.
 * A causa original é preservada para fins de log e diagnóstico.
 */
public class DatabaseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
