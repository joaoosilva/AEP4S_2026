package br.com.gestaosaude.dao;

import br.com.gestaosaude.exception.DatabaseException;
import br.com.gestaosaude.exception.NegocioException;
import br.com.gestaosaude.util.ConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Types;

/**
 * Utilitários internos da camada DAO: execução com/sem transação e tradução
 * de {@link SQLException} em exceções de domínio.
 */
final class JdbcUtils {

    /** Erros MySQL/MariaDB relevantes. */
    private static final int ER_DUP_ENTRY = 1062;
    private static final int ER_ROW_IS_REFERENCED = 1451;
    private static final int ER_NO_REFERENCED_ROW = 1452;
    private static final int ER_CHECK_CONSTRAINT_MYSQL = 3819;
    private static final int ER_CHECK_CONSTRAINT_MARIADB = 4025;

    private JdbcUtils() {
    }

    @FunctionalInterface
    interface SqlWork<T> {
        T executar(Connection conn) throws SQLException;
    }

    /** Executa {@code work} em uma conexão própria, em modo auto-commit. */
    static <T> T executar(String operacao, SqlWork<T> work) {
        try (Connection conn = ConnectionFactory.getConnection()) {
            return work.executar(conn);
        } catch (SQLException e) {
            throw traduzir(operacao, e);
        }
    }

    /** Executa {@code work} em uma transação: commit ao final ou rollback em qualquer falha. */
    static <T> T executarEmTransacao(String operacao, SqlWork<T> work) {
        try (Connection conn = ConnectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            try {
                T resultado = work.executar(conn);
                conn.commit();
                return resultado;
            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw traduzir(operacao, e);
        }
    }

    /** Converte violações de integridade em mensagens amigáveis; o resto vira {@link DatabaseException}. */
    static RuntimeException traduzir(String operacao, SQLException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        int codigo = e.getErrorCode();

        if (codigo == ER_DUP_ENTRY) {
            if (msg.contains("uk_pessoa_cpf")) {
                return new NegocioException("Já existe uma pessoa cadastrada com este CPF.", e);
            }
            if (msg.contains("uk_paciente_cartaoSus")) {
                return new NegocioException("Já existe um paciente com este Cartão SUS.", e);
            }
            if (msg.contains("uk_profissional_registro")) {
                return new NegocioException("Já existe um profissional com este registro de conselho.", e);
            }
            if (msg.contains("uk_atendimento_triagem")) {
                return new NegocioException("Esta triagem já está vinculada a outro atendimento.", e);
            }
            return new NegocioException("Registro duplicado.", e);
        }
        if (codigo == ER_ROW_IS_REFERENCED) {
            return new NegocioException("O registro possui vínculos (ex.: atendimentos) e não pode ser excluído.", e);
        }
        if (codigo == ER_NO_REFERENCED_ROW) {
            return new NegocioException("Referência inválida: o registro relacionado não existe.", e);
        }
        if (codigo == ER_CHECK_CONSTRAINT_MYSQL || codigo == ER_CHECK_CONSTRAINT_MARIADB) {
            return new NegocioException("Dados fora dos limites permitidos pelo banco de dados.", e);
        }
        if (e instanceof SQLIntegrityConstraintViolationException) {
            return new NegocioException("Violação de integridade dos dados.", e);
        }
        return new DatabaseException("Erro de banco de dados ao " + operacao + ".", e);
    }

    // ---------------------------------------------------------------- helpers de parâmetros

    static void setStringOuNull(PreparedStatement ps, int idx, String valor) throws SQLException {
        if (valor == null || valor.isBlank()) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, valor.trim());
        }
    }

    /** Inteiros {@code <= 0} são gravados como NULL ("não aferido"). */
    static void setIntPositivoOuNull(PreparedStatement ps, int idx, int valor) throws SQLException {
        if (valor <= 0) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, valor);
        }
    }

    static void setDoublePositivoOuNull(PreparedStatement ps, int idx, double valor) throws SQLException {
        if (valor <= 0) {
            ps.setNull(idx, Types.DECIMAL);
        } else {
            ps.setBigDecimal(idx, java.math.BigDecimal.valueOf(valor));
        }
    }

    static void setIntegerOuNull(PreparedStatement ps, int idx, Integer valor) throws SQLException {
        if (valor == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, valor);
        }
    }
}
