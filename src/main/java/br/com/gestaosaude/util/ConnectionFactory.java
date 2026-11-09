package br.com.gestaosaude.util;

import br.com.gestaosaude.exception.DatabaseException;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fábrica centralizada de conexões JDBC com o MySQL.
 * <p>
 * <b>Padrões aplicados:</b>
 * <ul>
 *   <li><b>Singleton</b> – a configuração (URL/usuário/senha) é carregada uma única vez,
 *       de forma thread-safe, na inicialização da classe (idiom <i>static holder</i>).</li>
 *   <li><b>Factory</b> – {@link #getConnection()} fabrica uma nova {@link Connection} a cada chamada.
 *       Não se compartilha uma única conexão global, pois {@code Connection} não é thread-safe
 *       e uma conexão caída derrubaria toda a aplicação.</li>
 * </ul>
 * <b>Ordem de precedência da configuração:</b> System Property ({@code -Ddb.user=...})
 * &gt; arquivo {@code database.properties} no classpath &gt; valores padrão.
 */
public final class ConnectionFactory {

    private static final Logger LOGGER = Logger.getLogger(ConnectionFactory.class.getName());

    private static final String PROPERTIES_FILE = "database.properties";

    private static final String KEY_URL      = "db.url";
    private static final String KEY_USER     = "db.user";
    private static final String KEY_PASSWORD = "db.password";

    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/gestao_saude_db"
            + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String DEFAULT_USER     = "root";
    private static final String DEFAULT_PASSWORD = "";

    private ConnectionFactory() {
        throw new AssertionError("Classe utilitária - não instanciar.");
    }

    /**
     * Holder da configuração: carregado de forma preguiçosa e thread-safe pela JVM
     * no primeiro acesso a {@code Config.INSTANCE}.
     */
    private static final class Config {
        private static final Config INSTANCE = load();

        private final String url;
        private final String user;
        private final String password;

        private Config(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }

        private static Config load() {
            Properties props = new Properties();
            try (InputStream in = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream(PROPERTIES_FILE)) {
                if (in != null) {
                    props.load(in);
                } else {
                    LOGGER.warning(() -> "Arquivo '" + PROPERTIES_FILE
                            + "' não encontrado no classpath. Usando valores padrão.");
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Falha ao ler '" + PROPERTIES_FILE
                        + "'. Usando valores padrão.", e);
            }
            return new Config(
                    resolve(props, KEY_URL, DEFAULT_URL),
                    resolve(props, KEY_USER, DEFAULT_USER),
                    resolve(props, KEY_PASSWORD, DEFAULT_PASSWORD));
        }

        private static String resolve(Properties props, String key, String defaultValue) {
            String value = System.getProperty(key);
            if (value == null) {
                value = props.getProperty(key);
            }
            if (value == null) {
                return defaultValue;
            }
            // URL vazia cai no padrão; usuário/senha em branco são aceitos (ex.: login anônimo local).
            if (value.isBlank() && KEY_URL.equals(key)) {
                return defaultValue;
            }
            return value.trim();
        }
    }

    /**
     * Abre uma nova conexão com o banco. O chamador é responsável por fechá-la,
     * preferencialmente com try-with-resources ou {@link #closeConnection}.
     *
     * @return conexão JDBC aberta
     * @throws DatabaseException se não for possível conectar
     */
    public static Connection getConnection() {
        Config cfg = Config.INSTANCE;
        try {
            return DriverManager.getConnection(cfg.url, cfg.user, cfg.password);
        } catch (SQLException e) {
            // Não expõe a senha; a SQLException original fica como causa para o log.
            throw new DatabaseException("Não foi possível conectar ao banco de dados.", e);
        }
    }

    /**
     * Fecha os recursos JDBC com segurança, na ordem inversa de abertura
     * (ResultSet → Statement → Connection). Aceita {@code null} em qualquer argumento
     * e nunca lança exceção: falhas no fechamento são apenas registradas em log,
     * para não mascarar uma exceção original do bloco principal.
     */
    public static void closeConnection(Connection conn, Statement stmt, ResultSet rs) {
        closeQuietly(rs);
        closeQuietly(stmt);
        closeQuietly(conn);
    }

    public static void closeConnection(Connection conn, Statement stmt) {
        closeConnection(conn, stmt, null);
    }

    public static void closeConnection(Connection conn) {
        closeConnection(conn, null, null);
    }

    private static void closeQuietly(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Falha ao fechar recurso JDBC: "
                    + resource.getClass().getSimpleName(), e);
        }
    }
}
