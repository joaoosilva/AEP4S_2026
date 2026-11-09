package br.com.gestaosaude.dao;

import br.com.gestaosaude.exception.NegocioException;
import br.com.gestaosaude.model.Paciente;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CRUD de pacientes (tabelas {@code pessoa} + {@code paciente}).
 */
public class PacienteDAO {

    private static final String SQL_SELECT_BASE =
            "SELECT p.id, p.nome, p.cpf, p.telefone, pa.cartaoSus, pa.dataNascimento "
            + "FROM paciente pa INNER JOIN pessoa p ON p.id = pa.id_pessoa ";
    private static final String SQL_LISTAR = SQL_SELECT_BASE + "ORDER BY p.nome";
    private static final String SQL_POR_ID = SQL_SELECT_BASE + "WHERE p.id = ?";
    private static final String SQL_POR_CPF = SQL_SELECT_BASE + "WHERE p.cpf = ?";
    private static final String SQL_EXISTE = "SELECT 1 FROM paciente WHERE id_pessoa = ?";
    private static final String SQL_INSERIR =
            "INSERT INTO paciente (id_pessoa, cartaoSus, dataNascimento) VALUES (?, ?, ?)";
    private static final String SQL_ATUALIZAR =
            "UPDATE paciente SET cartaoSus = ?, dataNascimento = ? WHERE id_pessoa = ?";
    private static final String SQL_EXCLUIR = "DELETE FROM paciente WHERE id_pessoa = ?";

    private final PessoaDAO pessoaDAO = new PessoaDAO();

    /**
     * Insere o paciente. Se já existir uma {@code pessoa} com o mesmo CPF (ex.: um
     * profissional que agora é paciente), reaproveita o registro e atualiza nome/telefone.
     */
    public Paciente inserir(Paciente paciente) {
        int id = JdbcUtils.executarEmTransacao("cadastrar paciente", conn -> {
            Optional<Integer> existente = pessoaDAO.buscarIdPorCpfComBloqueio(conn, paciente.getCpf());
            int idPessoa;
            if (existente.isPresent()) {
                idPessoa = existente.get();
                if (existe(conn, idPessoa)) {
                    throw new NegocioException("Já existe um paciente cadastrado com o CPF "
                            + paciente.getCpf() + ".");
                }
                pessoaDAO.atualizar(conn, idPessoa, paciente);
            } else {
                idPessoa = pessoaDAO.inserir(conn, paciente);
            }
            try (PreparedStatement ps = conn.prepareStatement(SQL_INSERIR)) {
                ps.setInt(1, idPessoa);
                ps.setString(2, paciente.getCartaoSus());
                ps.setObject(3, paciente.getDataNascimento());
                ps.executeUpdate();
            }
            return idPessoa;
        });
        paciente.setId(id);
        return paciente;
    }

    public List<Paciente> listar() {
        return JdbcUtils.executar("listar pacientes", conn -> {
            List<Paciente> lista = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(SQL_LISTAR);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
            return lista;
        });
    }

    public Optional<Paciente> buscarPorId(int id) {
        return JdbcUtils.executar("buscar paciente", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(SQL_POR_ID)) {
                ps.setInt(1, id);
                return buscarUm(ps);
            }
        });
    }

    public Optional<Paciente> buscarPorCpf(String cpf) {
        return JdbcUtils.executar("buscar paciente", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(SQL_POR_CPF)) {
                ps.setString(1, cpf);
                return buscarUm(ps);
            }
        });
    }

    public void atualizar(Paciente paciente) {
        JdbcUtils.executarEmTransacao("atualizar paciente", conn -> {
            pessoaDAO.atualizar(conn, paciente.getId(), paciente);
            try (PreparedStatement ps = conn.prepareStatement(SQL_ATUALIZAR)) {
                ps.setString(1, paciente.getCartaoSus());
                ps.setObject(2, paciente.getDataNascimento());
                ps.setInt(3, paciente.getId());
                if (ps.executeUpdate() == 0) {
                    throw new NegocioException("Paciente com id " + paciente.getId() + " não encontrado.");
                }
            }
            return null;
        });
    }

    /**
     * Exclui o paciente. A linha em {@code pessoa} só é removida se a pessoa não
     * for também um profissional de saúde.
     *
     * @return {@code false} se o paciente não existia
     */
    public boolean excluir(int id) {
        return JdbcUtils.executarEmTransacao("excluir paciente", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(SQL_EXCLUIR)) {
                ps.setInt(1, id);
                if (ps.executeUpdate() == 0) {
                    return false;
                }
            }
            pessoaDAO.excluirSeOrfa(conn, id);
            return true;
        });
    }

    private boolean existe(Connection conn, int idPessoa) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_EXISTE)) {
            ps.setInt(1, idPessoa);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Optional<Paciente> buscarUm(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
        }
    }

    private Paciente mapear(ResultSet rs) throws SQLException {
        return new Paciente(
                rs.getInt("id"),
                rs.getString("nome"),
                rs.getString("cpf"),
                rs.getString("telefone"),
                rs.getString("cartaoSus"),
                rs.getObject("dataNascimento", LocalDate.class));
    }
}
