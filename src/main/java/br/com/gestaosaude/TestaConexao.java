package br.com.gestaosaude;

import br.com.gestaosaude.exception.DatabaseException;
import br.com.gestaosaude.util.ConnectionFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Teste manual de conectividade: abre conexão, executa {@code SELECT 1},
 * valida o retorno e fecha os recursos.
 * <p>
 * Execução: {@code mvn compile exec:java}
 */
public class TestaConexao {

    public static void main(String[] args) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        int exitCode = 0;

        try {
            conn = ConnectionFactory.getConnection();

            DatabaseMetaData meta = conn.getMetaData();
            System.out.println("Conectado: " + meta.getDatabaseProductName()
                    + " " + meta.getDatabaseProductVersion()
                    + " | catálogo: " + conn.getCatalog());

            stmt = conn.prepareStatement("SELECT 1");
            rs = stmt.executeQuery();

            if (rs.next() && rs.getInt(1) == 1) {
                System.out.println("[OK] Comunicação com o banco validada (SELECT 1 = 1).");
            } else {
                System.err.println("[FALHA] SELECT 1 retornou resultado inesperado.");
                exitCode = 1;
            }
        } catch (DatabaseException | SQLException e) {
            System.err.println("[FALHA] " + e.getMessage());
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            System.err.println("        Causa: " + cause.getMessage());
            exitCode = 1;
        } finally {
            ConnectionFactory.closeConnection(conn, stmt, rs);
            System.out.println("Recursos liberados.");
        }
        // Fora do finally: System.exit dentro do catch impediria o fechamento dos recursos.
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
