package br.com.gestaosaude.dao;

import br.com.gestaosaude.exception.NegocioException;
import br.com.gestaosaude.model.Pessoa;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * Operações sobre a tabela {@code pessoa}, compartilhadas por {@link PacienteDAO}
 * e {@link ProfissionalSaudeDAO}. Sempre recebe a conexão do chamador para
 * participar da mesma transação da especialização.
 */
final class PessoaDAO {

    private static final String SQL_ID_POR_CPF_LOCK =
            "SELECT id FROM pessoa WHERE cpf = ? FOR UPDATE";
    private static final String SQL_INSERIR =
            "INSERT INTO pessoa (nome, cpf, telefone) VALUES (?, ?, ?)";
    private static final String SQL_ATUALIZAR =
            "UPDATE pessoa SET nome = ?, cpf = ?, telefone = ? WHERE id = ?";
    /** Só remove a pessoa se ela não for mais paciente nem profissional. */
    private static final String SQL_EXCLUIR_SE_ORFA =
            "DELETE FROM pessoa WHERE id = ? "
            + "AND NOT EXISTS (SELECT 1 FROM paciente WHERE id_pessoa = ?) "
            + "AND NOT EXISTS (SELECT 1 FROM profissional_saude WHERE id_pessoa = ?)";

    /** Busca o id da pessoa pelo CPF, bloqueando a linha até o fim da transação. */
    Optional<Integer> buscarIdPorCpfComBloqueio(Connection conn, String cpf) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_ID_POR_CPF_LOCK)) {
            ps.setString(1, cpf);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty();
            }
        }
    }

    int inserir(Connection conn, Pessoa p) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERIR, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getNome());
            ps.setString(2, p.getCpf());
            JdbcUtils.setStringOuNull(ps, 3, p.getTelefone());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("ID da pessoa não foi gerado.");
                }
                return keys.getInt(1);
            }
        }
    }

    void atualizar(Connection conn, int id, Pessoa p) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_ATUALIZAR)) {
            ps.setString(1, p.getNome());
            ps.setString(2, p.getCpf());
            JdbcUtils.setStringOuNull(ps, 3, p.getTelefone());
            ps.setInt(4, id);
            if (ps.executeUpdate() == 0) {
                throw new NegocioException("Pessoa com id " + id + " não encontrada.");
            }
        }
    }

    void excluirSeOrfa(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_EXCLUIR_SE_ORFA)) {
            ps.setInt(1, id);
            ps.setInt(2, id);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }
}
