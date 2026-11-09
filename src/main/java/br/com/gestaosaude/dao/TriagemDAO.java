package br.com.gestaosaude.dao;

import br.com.gestaosaude.model.ClassificadorRisco;
import br.com.gestaosaude.model.SinaisVitais;
import br.com.gestaosaude.model.TriagemAdulto;
import br.com.gestaosaude.model.TriagemPediatrica;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

/**
 * Persistência das triagens. O tipo de classificador é gravado em {@code tipo_triagem}
 * (herança em tabela única) e reconstruído na leitura.
 */
public class TriagemDAO {

    public static final String TIPO_ADULTO = "ADULTO";
    public static final String TIPO_PEDIATRICA = "PEDIATRICA";

    private static final String SQL_INSERIR =
            "INSERT INTO triagem (tipo_triagem, pressaoArterial, frequenciaCardiaca, temperatura, "
            + "frequenciaRespiratoria, saturacaoOxigenio, faixaEtariaMeses) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_EXCLUIR = "DELETE FROM triagem WHERE id = ?";

    /** Insere a triagem usando a conexão (e transação) do chamador. */
    int inserir(Connection conn, ClassificadorRisco classificador, SinaisVitais s) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERIR, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tipoDe(classificador));
            JdbcUtils.setStringOuNull(ps, 2, s.getPressaoArterial());
            JdbcUtils.setIntPositivoOuNull(ps, 3, s.getFrequenciaCardiaca());
            JdbcUtils.setDoublePositivoOuNull(ps, 4, s.getTemperatura());
            JdbcUtils.setIntPositivoOuNull(ps, 5, s.getFrequenciaRespiratoria());
            JdbcUtils.setDoublePositivoOuNull(ps, 6, s.getSaturacaoOxigenio());
            if (classificador instanceof TriagemPediatrica ped) {
                ps.setInt(7, ped.getFaixaEtariaMeses());
            } else {
                ps.setNull(7, Types.INTEGER);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("ID da triagem não foi gerado.");
                }
                return keys.getInt(1);
            }
        }
    }

    public boolean excluir(int id) {
        return JdbcUtils.executar("excluir triagem", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(SQL_EXCLUIR)) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            }
        });
    }

    public static String tipoDe(ClassificadorRisco classificador) {
        if (classificador instanceof TriagemPediatrica) {
            return TIPO_PEDIATRICA;
        }
        if (classificador instanceof TriagemAdulto) {
            return TIPO_ADULTO;
        }
        throw new IllegalArgumentException("Classificador não suportado: " + classificador);
    }

    /** Reconstrói o classificador a partir das colunas da triagem. */
    static ClassificadorRisco criarClassificador(String tipo, Integer faixaEtariaMeses) {
        if (TIPO_PEDIATRICA.equals(tipo)) {
            return new TriagemPediatrica(faixaEtariaMeses == null ? 0 : faixaEtariaMeses);
        }
        return new TriagemAdulto();
    }

    /** Lê os sinais vitais das colunas da triagem (NULL vira 0 = "não aferido"). */
    static SinaisVitais mapearSinais(ResultSet rs) throws SQLException {
        return new SinaisVitais(
                rs.getString("pressaoArterial"),
                rs.getInt("frequenciaCardiaca"),
                decimal(rs.getBigDecimal("temperatura")),
                rs.getInt("frequenciaRespiratoria"),
                decimal(rs.getBigDecimal("saturacaoOxigenio")));
    }

    private static double decimal(BigDecimal valor) {
        return valor == null ? 0 : valor.doubleValue();
    }
}
