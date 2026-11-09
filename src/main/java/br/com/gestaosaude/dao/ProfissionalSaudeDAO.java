package br.com.gestaosaude.dao;

import br.com.gestaosaude.exception.NegocioException;
import br.com.gestaosaude.model.ProfissionalSaude;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CRUD de profissionais de saúde (tabelas {@code pessoa} + {@code profissional_saude}).
 */
public class ProfissionalSaudeDAO {

    private static final String SQL_SELECT_BASE =
            "SELECT p.id, p.nome, p.cpf, p.telefone, ps.registroConselho, ps.cargo "
            + "FROM profissional_saude ps INNER JOIN pessoa p ON p.id = ps.id_pessoa ";
    private static final String SQL_LISTAR = SQL_SELECT_BASE + "ORDER BY ps.cargo, p.nome";
    private static final String SQL_POR_ID = SQL_SELECT_BASE + "WHERE p.id = ?";
    private static final String SQL_POR_CPF = SQL_SELECT_BASE + "WHERE p.cpf = ?";
    private static final String SQL_POR_REGISTRO = SQL_SELECT_BASE + "WHERE ps.registroConselho = ?";
    private static final String SQL_EXISTE = "SELECT 1 FROM profissional_saude WHERE id_pessoa = ?";
    private static final String SQL_INSERIR =
            "INSERT INTO profissional_saude (id_pessoa, registroConselho, cargo) VALUES (?, ?, ?)";
    private static final String SQL_ATUALIZAR =
            "UPDATE profissional_saude SET registroConselho = ?, cargo = ? WHERE id_pessoa = ?";
    private static final String SQL_EXCLUIR = "DELETE FROM profissional_saude WHERE id_pessoa = ?";

    private final PessoaDAO pessoaDAO = new PessoaDAO();

    /** Insere o profissional, reaproveitando a {@code pessoa} caso o CPF já exista (ex.: já é paciente). */
    public ProfissionalSaude inserir(ProfissionalSaude prof) {
        int id = JdbcUtils.executarEmTransacao("cadastrar profissional", conn -> {
            Optional<Integer> existente = pessoaDAO.buscarIdPorCpfComBloqueio(conn, prof.getCpf());
            int idPessoa;
            if (existente.isPresent()) {
                idPessoa = existente.get();
                if (existe(conn, idPessoa)) {
                    throw new NegocioException("Já existe um profissional cadastrado com o CPF "
                            + prof.getCpf() + ".");
                }
                pessoaDAO.atualizar(conn, idPessoa, prof);
            } else {
                idPessoa = pessoaDAO.inserir(conn, prof);
            }
            try (PreparedStatement ps = conn.prepareStatement(SQL_INSERIR)) {
                ps.setInt(1, idPessoa);
                ps.setString(2, prof.getRegistroConselho());
                ps.setString(3, prof.getCargo());
                ps.executeUpdate();
            }
            return idPessoa;
        });
        prof.setId(id);
        return prof;
    }

    public List<ProfissionalSaude> listar() {
        return JdbcUtils.executar("listar profissionais", conn -> {
            List<ProfissionalSaude> lista = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(SQL_LISTAR);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
            return lista;
        });
    }

    public Optional<ProfissionalSaude> buscarPorId(int id) {
        return buscarPor(SQL_POR_ID, ps -> ps.setInt(1, id));
    }

    public Optional<ProfissionalSaude> buscarPorCpf(String cpf) {
        return buscarPor(SQL_POR_CPF, ps -> ps.setString(1, cpf));
    }

    public Optional<ProfissionalSaude> buscarPorRegistro(String registroConselho) {
        return buscarPor(SQL_POR_REGISTRO, ps -> ps.setString(1, registroConselho));
    }

    public void atualizar(ProfissionalSaude prof) {
        JdbcUtils.executarEmTransacao("atualizar profissional", conn -> {
            pessoaDAO.atualizar(conn, prof.getId(), prof);
            try (PreparedStatement ps = conn.prepareStatement(SQL_ATUALIZAR)) {
                ps.setString(1, prof.getRegistroConselho());
                ps.setString(2, prof.getCargo());
                ps.setInt(3, prof.getId());
                if (ps.executeUpdate() == 0) {
                    throw new NegocioException("Profissional com id " + prof.getId() + " não encontrado.");
                }
            }
            return null;
        });
    }

    /**
     * Exclui o profissional (atendimentos vinculados ficam com profissional NULL, conforme o DER).
     * A {@code pessoa} só é removida se não for também paciente.
     */
    public boolean excluir(int id) {
        return JdbcUtils.executarEmTransacao("excluir profissional", conn -> {
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

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private Optional<ProfissionalSaude> buscarPor(String sql, Binder binder) {
        return JdbcUtils.executar("buscar profissional", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                binder.bind(ps);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
                }
            }
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

    private ProfissionalSaude mapear(ResultSet rs) throws SQLException {
        return new ProfissionalSaude(
                rs.getInt("id"),
                rs.getString("nome"),
                rs.getString("cpf"),
                rs.getString("telefone"),
                rs.getString("registroConselho"),
                rs.getString("cargo"));
    }
}
