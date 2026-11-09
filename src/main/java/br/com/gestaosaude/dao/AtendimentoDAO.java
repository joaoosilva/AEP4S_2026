package br.com.gestaosaude.dao;

import br.com.gestaosaude.exception.NegocioException;
import br.com.gestaosaude.model.Atendimento;
import br.com.gestaosaude.model.ClassificadorRisco;
import br.com.gestaosaude.model.Paciente;
import br.com.gestaosaude.model.ProfissionalSaude;
import br.com.gestaosaude.model.SinaisVitais;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistência de atendimentos. As consultas trazem paciente, profissional e triagem
 * em um único SELECT (evita o problema N+1).
 * <p>
 * A prioridade não é armazenada: é recalculada a partir dos dados da triagem
 * (dado derivado, mantendo o modelo normalizado).
 */
public class AtendimentoDAO {

    private static final String SQL_SELECT_BASE =
            "SELECT a.id, a.dataHoraChegada, a.status, a.fila_codigo_unidade, a.triagem_id, "
            + "       pp.id AS pac_id, pp.nome AS pac_nome, pp.cpf AS pac_cpf, pp.telefone AS pac_tel, "
            + "       pa.cartaoSus, pa.dataNascimento, "
            + "       ps.id AS prof_id, ps.nome AS prof_nome, ps.cpf AS prof_cpf, ps.telefone AS prof_tel, "
            + "       pr.registroConselho, pr.cargo, "
            + "       t.tipo_triagem, t.pressaoArterial, t.frequenciaCardiaca, t.temperatura, "
            + "       t.frequenciaRespiratoria, t.saturacaoOxigenio, t.faixaEtariaMeses "
            + "FROM atendimento a "
            + "INNER JOIN paciente pa ON pa.id_pessoa = a.paciente_id "
            + "INNER JOIN pessoa pp ON pp.id = pa.id_pessoa "
            + "LEFT JOIN profissional_saude pr ON pr.id_pessoa = a.profissional_id "
            + "LEFT JOIN pessoa ps ON ps.id = pr.id_pessoa "
            + "LEFT JOIN triagem t ON t.id = a.triagem_id ";

    private static final String SQL_POR_ID = SQL_SELECT_BASE + "WHERE a.id = ?";
    private static final String SQL_POR_UNIDADE_STATUS = SQL_SELECT_BASE
            + "WHERE a.fila_codigo_unidade = ? AND a.status = ? ORDER BY a.dataHoraChegada, a.id";
    private static final String SQL_POR_PACIENTE = SQL_SELECT_BASE
            + "WHERE a.paciente_id = ? ORDER BY a.dataHoraChegada DESC, a.id DESC";
    private static final String SQL_ATIVO_POR_PACIENTE = SQL_SELECT_BASE
            + "WHERE a.paciente_id = ? AND a.status IN ('AGUARDANDO', 'EM_ATENDIMENTO') "
            + "ORDER BY a.dataHoraChegada LIMIT 1";
    private static final String SQL_EM_ATENDIMENTO_POR_PROFISSIONAL = SQL_SELECT_BASE
            + "WHERE a.profissional_id = ? AND a.status = 'EM_ATENDIMENTO' LIMIT 1";
    private static final String SQL_EXISTE_POR_PACIENTE =
            "SELECT COUNT(*) FROM atendimento WHERE paciente_id = ?";

    private static final String SQL_INSERIR =
            "INSERT INTO atendimento (dataHoraChegada, status, paciente_id, profissional_id, "
            + "triagem_id, fila_codigo_unidade) VALUES (?, ?, ?, ?, ?, ?)";
    /** Atualização condicionada ao status esperado (controle de concorrência otimista). */
    private static final String SQL_ATUALIZAR_STATUS =
            "UPDATE atendimento SET status = ?, profissional_id = ? WHERE id = ? AND status = ?";
    private static final String SQL_VINCULAR_TRIAGEM =
            "UPDATE atendimento SET triagem_id = ? WHERE id = ? AND triagem_id IS NULL AND status = 'AGUARDANDO'";
    private static final String SQL_EXCLUIR = "DELETE FROM atendimento WHERE id = ?";

    private final TriagemDAO triagemDAO = new TriagemDAO();

    /**
     * Insere o atendimento e, se houver, sua triagem, na mesma transação.
     */
    public Atendimento inserir(Atendimento a) {
        int[] ids = JdbcUtils.executarEmTransacao("registrar atendimento", conn -> {
            Integer triagemId = null;
            if (a.isTriado()) {
                triagemId = triagemDAO.inserir(conn, a.getClassificador(), a.getSinaisVitais());
            }
            try (PreparedStatement ps = conn.prepareStatement(SQL_INSERIR, Statement.RETURN_GENERATED_KEYS)) {
                ps.setObject(1, a.getDataHoraChegada());
                ps.setString(2, a.getStatus());
                ps.setInt(3, a.getPaciente().getId());
                JdbcUtils.setIntegerOuNull(ps, 4, a.getProfissional() == null ? null : a.getProfissional().getId());
                JdbcUtils.setIntegerOuNull(ps, 5, triagemId);
                ps.setString(6, a.getCodigoUnidade());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("ID do atendimento não foi gerado.");
                    }
                    return new int[] {keys.getInt(1), triagemId == null ? 0 : triagemId};
                }
            }
        });
        a.setId(ids[0]);
        a.setTriagemId(ids[1] == 0 ? null : ids[1]);
        return a;
    }

    /**
     * Grava a triagem de um atendimento que entrou na fila sem triagem.
     * O atendimento precisa estar AGUARDANDO e ainda sem triagem.
     */
    public void vincularTriagem(Atendimento a) {
        if (!a.isTriado()) {
            throw new IllegalArgumentException("Atendimento sem dados de triagem.");
        }
        int triagemId = JdbcUtils.executarEmTransacao("registrar triagem", conn -> {
            int id = triagemDAO.inserir(conn, a.getClassificador(), a.getSinaisVitais());
            try (PreparedStatement ps = conn.prepareStatement(SQL_VINCULAR_TRIAGEM)) {
                ps.setInt(1, id);
                ps.setInt(2, a.getId());
                if (ps.executeUpdate() == 0) {
                    throw new NegocioException("O atendimento " + a.getId()
                            + " não está mais aguardando triagem.");
                }
            }
            return id;
        });
        a.setTriagemId(triagemId);
    }

    /**
     * Persiste status e profissional somente se o status no banco ainda for {@code statusEsperado}.
     *
     * @return {@code false} se outro usuário alterou o atendimento antes
     */
    public boolean atualizarStatus(Atendimento a, String statusEsperado) {
        return JdbcUtils.executar("atualizar atendimento", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(SQL_ATUALIZAR_STATUS)) {
                ps.setString(1, a.getStatus());
                JdbcUtils.setIntegerOuNull(ps, 2, a.getProfissional() == null ? null : a.getProfissional().getId());
                ps.setInt(3, a.getId());
                ps.setString(4, statusEsperado);
                return ps.executeUpdate() > 0;
            }
        });
    }

    public Optional<Atendimento> buscarPorId(int id) {
        return buscarUm(SQL_POR_ID, id);
    }

    public List<Atendimento> listarPorUnidadeEStatus(String codigoUnidade, String status) {
        return JdbcUtils.executar("listar fila", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(SQL_POR_UNIDADE_STATUS)) {
                ps.setString(1, codigoUnidade);
                ps.setString(2, status);
                return listar(ps);
            }
        });
    }

    public List<Atendimento> listarPorPaciente(int pacienteId) {
        return JdbcUtils.executar("consultar histórico", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(SQL_POR_PACIENTE)) {
                ps.setInt(1, pacienteId);
                return listar(ps);
            }
        });
    }

    /** Atendimento AGUARDANDO ou EM_ATENDIMENTO do paciente, se houver. */
    public Optional<Atendimento> buscarAtivoPorPaciente(int pacienteId) {
        return buscarUm(SQL_ATIVO_POR_PACIENTE, pacienteId);
    }

    public Optional<Atendimento> buscarEmAtendimentoPorProfissional(int profissionalId) {
        return buscarUm(SQL_EM_ATENDIMENTO_POR_PROFISSIONAL, profissionalId);
    }

    public int contarPorPaciente(int pacienteId) {
        return JdbcUtils.executar("consultar atendimentos", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(SQL_EXISTE_POR_PACIENTE)) {
                ps.setInt(1, pacienteId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    public boolean excluir(int id) {
        return JdbcUtils.executar("excluir atendimento", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(SQL_EXCLUIR)) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            }
        });
    }

    // ---------------------------------------------------------------- mapeamento

    private Optional<Atendimento> buscarUm(String sql, int parametro) {
        return JdbcUtils.executar("buscar atendimento", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, parametro);
                List<Atendimento> lista = listar(ps);
                return lista.isEmpty() ? Optional.empty() : Optional.of(lista.get(0));
            }
        });
    }

    private List<Atendimento> listar(PreparedStatement ps) throws SQLException {
        List<Atendimento> lista = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    private Atendimento mapear(ResultSet rs) throws SQLException {
        Atendimento a = new Atendimento();
        a.setId(rs.getInt("id"));
        a.setDataHoraChegada(rs.getObject("dataHoraChegada", LocalDateTime.class));
        a.setStatus(rs.getString("status"));
        a.setCodigoUnidade(rs.getString("fila_codigo_unidade"));

        a.setPaciente(new Paciente(
                rs.getInt("pac_id"),
                rs.getString("pac_nome"),
                rs.getString("pac_cpf"),
                rs.getString("pac_tel"),
                rs.getString("cartaoSus"),
                rs.getObject("dataNascimento", LocalDate.class)));

        int profId = rs.getInt("prof_id");
        if (!rs.wasNull()) {
            a.setProfissional(new ProfissionalSaude(
                    profId,
                    rs.getString("prof_nome"),
                    rs.getString("prof_cpf"),
                    rs.getString("prof_tel"),
                    rs.getString("registroConselho"),
                    rs.getString("cargo")));
        }

        int triagemId = rs.getInt("triagem_id");
        if (!rs.wasNull()) {
            int faixa = rs.getInt("faixaEtariaMeses");
            Integer faixaEtaria = rs.wasNull() ? null : faixa;
            ClassificadorRisco classificador =
                    TriagemDAO.criarClassificador(rs.getString("tipo_triagem"), faixaEtaria);
            SinaisVitais sinais = TriagemDAO.mapearSinais(rs);
            a.setTriagemId(triagemId);
            a.setClassificador(classificador);
            a.setSinaisVitais(sinais);
        }
        return a;
    }
}
