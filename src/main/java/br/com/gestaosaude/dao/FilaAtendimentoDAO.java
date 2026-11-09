package br.com.gestaosaude.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * CRUD das filas/unidades de saúde (tabela {@code fila_atendimento}).
 */
public class FilaAtendimentoDAO {

    private static final String SQL_LISTAR =
            "SELECT codigoUnidade FROM fila_atendimento ORDER BY codigoUnidade";
    private static final String SQL_EXISTE =
            "SELECT 1 FROM fila_atendimento WHERE codigoUnidade = ?";
    private static final String SQL_INSERIR =
            "INSERT INTO fila_atendimento (codigoUnidade) VALUES (?)";
    private static final String SQL_EXCLUIR =
            "DELETE FROM fila_atendimento WHERE codigoUnidade = ?";

    public List<String> listarCodigos() {
        return JdbcUtils.executar("listar unidades", conn -> {
            List<String> codigos = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(SQL_LISTAR);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    codigos.add(rs.getString(1));
                }
            }
            return codigos;
        });
    }

    public boolean existe(String codigoUnidade) {
        return JdbcUtils.executar("consultar unidade", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(SQL_EXISTE)) {
                ps.setString(1, codigoUnidade);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    public void inserir(String codigoUnidade) {
        JdbcUtils.executar("cadastrar unidade", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(SQL_INSERIR)) {
                ps.setString(1, codigoUnidade);
                return ps.executeUpdate();
            }
        });
    }

    /** Falha (com mensagem amigável) se a unidade tiver atendimentos vinculados. */
    public boolean excluir(String codigoUnidade) {
        return JdbcUtils.executar("excluir unidade", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(SQL_EXCLUIR)) {
                ps.setString(1, codigoUnidade);
                return ps.executeUpdate() > 0;
            }
        });
    }
}
