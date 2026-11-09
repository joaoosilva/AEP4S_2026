package br.com.gestaosaude.view;

import br.com.gestaosaude.dao.AtendimentoDAO;
import br.com.gestaosaude.dao.FilaAtendimentoDAO;
import br.com.gestaosaude.dao.PacienteDAO;
import br.com.gestaosaude.dao.ProfissionalSaudeDAO;
import br.com.gestaosaude.exception.DatabaseException;
import br.com.gestaosaude.exception.NegocioException;
import br.com.gestaosaude.model.Atendimento;
import br.com.gestaosaude.model.ClassificadorRisco;
import br.com.gestaosaude.model.FilaAtendimento;
import br.com.gestaosaude.model.Paciente;
import br.com.gestaosaude.model.ProfissionalSaude;
import br.com.gestaosaude.model.SinaisVitais;
import br.com.gestaosaude.service.FilaAtendimentoService;
import br.com.gestaosaude.service.PacienteService;
import br.com.gestaosaude.service.ProfissionalSaudeService;
import br.com.gestaosaude.service.TriagemService;
import br.com.gestaosaude.util.ConnectionFactory;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Interface de console (CLI) do Sistema de Gestão de Filas e Priorização.
 * <p>
 * Execução: {@code mvn compile exec:java}
 */
public class MenuPrincipal {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String LINHA = "-".repeat(110);

    private final ConsoleIO io;
    private final PrintStream out;
    private final PacienteService pacienteService;
    private final ProfissionalSaudeService profissionalService;
    private final TriagemService triagemService;
    private final FilaAtendimentoService filaService;

    public MenuPrincipal(ConsoleIO io, PrintStream out, PacienteService pacienteService,
                         ProfissionalSaudeService profissionalService, TriagemService triagemService,
                         FilaAtendimentoService filaService) {
        this.io = io;
        this.out = out;
        this.pacienteService = pacienteService;
        this.profissionalService = profissionalService;
        this.triagemService = triagemService;
        this.filaService = filaService;
    }

    public static void main(String[] args) {
        PrintStream out = System.out;
        if (!verificarConexao(out)) {
            System.exit(1);
        }

        // Composição manual das dependências (injeção via construtor).
        AtendimentoDAO atendimentoDAO = new AtendimentoDAO();
        PacienteService pacienteService = new PacienteService(new PacienteDAO(), atendimentoDAO);
        ProfissionalSaudeService profissionalService = new ProfissionalSaudeService(new ProfissionalSaudeDAO());
        TriagemService triagemService = new TriagemService();
        FilaAtendimentoService filaService = new FilaAtendimentoService(atendimentoDAO, new FilaAtendimentoDAO(),
                pacienteService, profissionalService, triagemService);

        ConsoleIO io = new ConsoleIO(new Scanner(System.in), out);
        try {
            new MenuPrincipal(io, out, pacienteService, profissionalService, triagemService, filaService).executar();
        } catch (ConsoleIO.EntradaEncerradaException e) {
            out.println("\nEntrada encerrada. Até logo!");
        }
    }

    private static boolean verificarConexao(PrintStream out) {
        try (Connection conn = ConnectionFactory.getConnection()) {
            return !conn.isClosed();
        } catch (DatabaseException | SQLException e) {
            out.println("Não foi possível conectar ao banco de dados.");
            out.println("Causa: " + causaRaiz(e));
            out.println("Verifique o MySQL e o arquivo src/main/resources/database.properties.");
            return false;
        }
    }

    // ===================================================================== menu principal

    public void executar() {
        while (true) {
            out.println();
            out.println("==================================================================");
            out.println("   SISTEMA DE GESTÃO DE FILAS E PRIORIZAÇÃO - UNIDADES DE SAÚDE");
            out.println("==================================================================");
            out.println(" 1. Cadastrar Paciente");
            out.println(" 2. Gerenciar Pacientes");
            out.println(" 3. Cadastrar/Consultar Profissional");
            out.println(" 4. Nova Triagem e Inserção na Fila");
            out.println(" 5. Ver Fila Priorizada");
            out.println(" 6. Chamar/Atender Próximo");
            out.println(" 7. Histórico de Paciente");
            out.println(" 0. Sair");
            int opcao = io.lerInt("Opção: ", 0, 7);
            switch (opcao) {
                case 0 -> {
                    out.println("Até logo!");
                    return;
                }
                case 1 -> comTratamento(this::cadastrarPaciente);
                case 2 -> menuPacientes();
                case 3 -> menuProfissionais();
                case 4 -> comTratamento(this::novaTriagem);
                case 5 -> comTratamento(this::verFila);
                case 6 -> menuAtendimento();
                case 7 -> comTratamento(this::historicoPaciente);
                default -> throw new IllegalStateException("Opção inesperada: " + opcao);
            }
        }
    }

    /** Executa a ação exibindo erros de negócio/banco sem derrubar a aplicação. */
    private void comTratamento(Runnable acao) {
        try {
            acao.run();
        } catch (NegocioException e) {
            erro(e.getMessage());
        } catch (DatabaseException e) {
            erro(e.getMessage() + " Detalhe técnico: " + causaRaiz(e));
        }
    }

    // ===================================================================== pacientes (RF01/RF02)

    private void cadastrarPaciente() {
        titulo("CADASTRAR PACIENTE");
        String nome = io.lerTexto("Nome completo: ");
        String cpf = io.lerTexto("CPF: ");
        String telefone = io.lerTextoOpcional("Telefone (opcional): ");
        String cartaoSus = io.lerTexto("Cartão SUS (15 dígitos): ");
        var nascimento = io.lerData("Data de nascimento (dd/mm/aaaa): ");

        Paciente p = pacienteService.cadastrar(new Paciente(nome, cpf, telefone, cartaoSus, nascimento));
        sucesso("Paciente cadastrado com ID " + p.getId() + ".");
    }

    private void menuPacientes() {
        while (true) {
            titulo("GERENCIAR PACIENTES");
            out.println(" 1. Listar todos");
            out.println(" 2. Buscar por CPF");
            out.println(" 3. Buscar por ID");
            out.println(" 4. Alterar dados");
            out.println(" 5. Excluir");
            out.println(" 0. Voltar");
            int opcao = io.lerInt("Opção: ", 0, 5);
            switch (opcao) {
                case 0 -> {
                    return;
                }
                case 1 -> comTratamento(this::listarPacientes);
                case 2 -> comTratamento(() -> detalharPaciente(
                        pacienteService.buscarPorCpf(io.lerTexto("CPF: "))));
                case 3 -> comTratamento(() -> detalharPaciente(
                        pacienteService.buscarPorId(io.lerInt("ID: ", 1, Integer.MAX_VALUE))));
                case 4 -> comTratamento(this::alterarPaciente);
                case 5 -> comTratamento(this::excluirPaciente);
                default -> throw new IllegalStateException("Opção inesperada: " + opcao);
            }
        }
    }

    private void listarPacientes() {
        List<Paciente> pacientes = pacienteService.listar();
        if (pacientes.isEmpty()) {
            out.println("Nenhum paciente cadastrado.");
            return;
        }
        String formato = "%-5s %-30s %-15s %-16s %-11s %-9s %-16s%n";
        out.printf(formato, "ID", "Nome", "CPF", "Cartão SUS", "Nascimento", "Idade", "Telefone");
        out.println(LINHA);
        for (Paciente p : pacientes) {
            out.printf(formato, p.getId(), cortar(p.getNome(), 30), p.getCpf(), p.getCartaoSus(),
                    ConsoleIO.FORMATO_DATA.format(p.getDataNascimento()), idade(p), valor(p.getTelefone()));
        }
        out.println(pacientes.size() + " paciente(s).");
    }

    private void detalharPaciente(Paciente p) {
        out.println();
        out.println("ID .............: " + p.getId());
        out.println("Nome ...........: " + p.getNome());
        out.println("CPF ............: " + p.getCpf());
        out.println("Telefone .......: " + valor(p.getTelefone()));
        out.println("Cartão SUS .....: " + p.getCartaoSus());
        out.println("Nascimento .....: " + ConsoleIO.FORMATO_DATA.format(p.getDataNascimento())
                + " (" + idade(p) + ")");
        out.println("Triagem aplicável: " + (p.isPediatrico() ? "Pediátrica" : "Adulto"));
    }

    private void alterarPaciente() {
        Paciente p = selecionarPaciente();
        detalharPaciente(p);
        out.println("\nDeixe em branco para manter o valor atual.");
        p.setNome(io.lerTextoOuManter("Nome", p.getNome()));
        p.setCpf(io.lerTextoOuManter("CPF", p.getCpf()));
        p.setTelefone(io.lerTextoOuManter("Telefone", p.getTelefone()));
        p.setCartaoSus(io.lerTextoOuManter("Cartão SUS", p.getCartaoSus()));
        p.setDataNascimento(io.lerDataOuManter("Data de nascimento", p.getDataNascimento()));
        pacienteService.atualizar(p);
        sucesso("Paciente atualizado.");
    }

    private void excluirPaciente() {
        Paciente p = selecionarPaciente();
        detalharPaciente(p);
        if (io.confirmar("Confirma a exclusão de " + p.getNome() + "?")) {
            pacienteService.excluir(p.getId());
            sucesso("Paciente excluído.");
        } else {
            out.println("Exclusão cancelada.");
        }
    }

    /** Aceita CPF (com ou sem máscara) ou ID numérico. */
    private Paciente selecionarPaciente() {
        String entrada = io.lerTexto("CPF ou ID do paciente: ");
        String digitos = entrada.replaceAll("\\D", "");
        if (digitos.length() == 11 || entrada.contains(".") || entrada.contains("-")) {
            return pacienteService.buscarPorCpf(entrada);
        }
        try {
            return pacienteService.buscarPorId(Integer.parseInt(entrada));
        } catch (NumberFormatException e) {
            throw new NegocioException("Informe um CPF válido ou o ID numérico do paciente.");
        }
    }

    // ===================================================================== profissionais (RF03)

    private void menuProfissionais() {
        while (true) {
            titulo("PROFISSIONAIS DE SAÚDE");
            out.println(" 1. Cadastrar profissional");
            out.println(" 2. Listar profissionais");
            out.println(" 3. Buscar por CPF ou registro no conselho");
            out.println(" 0. Voltar");
            int opcao = io.lerInt("Opção: ", 0, 3);
            switch (opcao) {
                case 0 -> {
                    return;
                }
                case 1 -> comTratamento(this::cadastrarProfissional);
                case 2 -> comTratamento(this::listarProfissionais);
                case 3 -> comTratamento(this::buscarProfissional);
                default -> throw new IllegalStateException("Opção inesperada: " + opcao);
            }
        }
    }

    private void cadastrarProfissional() {
        titulo("CADASTRAR PROFISSIONAL");
        String nome = io.lerTexto("Nome completo: ");
        String cpf = io.lerTexto("CPF: ");
        String telefone = io.lerTextoOpcional("Telefone (opcional): ");
        String registro = io.lerTexto("Registro no conselho (ex.: CRM-SP 123456): ");
        out.println("Cargo: 1. Médico  2. Enfermeiro  3. Técnico de Enfermagem  4. Outro");
        String cargo = switch (io.lerInt("Opção: ", 1, 4)) {
            case 1 -> "Médico";
            case 2 -> "Enfermeiro";
            case 3 -> "Técnico de Enfermagem";
            default -> io.lerTexto("Informe o cargo: ");
        };
        ProfissionalSaude p = profissionalService.cadastrar(
                new ProfissionalSaude(nome, cpf, telefone, registro, cargo));
        sucesso("Profissional cadastrado com ID " + p.getId() + ".");
    }

    private void listarProfissionais() {
        List<ProfissionalSaude> lista = profissionalService.listar();
        if (lista.isEmpty()) {
            out.println("Nenhum profissional cadastrado.");
            return;
        }
        String formato = "%-5s %-32s %-15s %-22s %-20s%n";
        out.printf(formato, "ID", "Nome", "CPF", "Cargo", "Registro");
        out.println(LINHA);
        for (ProfissionalSaude p : lista) {
            out.printf(formato, p.getId(), cortar(p.getNome(), 32), p.getCpf(), cortar(p.getCargo(), 22),
                    p.getRegistroConselho());
        }
    }

    private void buscarProfissional() {
        String entrada = io.lerTexto("CPF ou registro: ");
        boolean pareceCpf = entrada.matches("[\\d.\\-\\s]+") && entrada.replaceAll("\\D", "").length() == 11;
        ProfissionalSaude p = pareceCpf
                ? profissionalService.buscarPorCpf(entrada)
                : profissionalService.buscarPorRegistro(entrada.toUpperCase());
        out.println();
        out.println("ID ......: " + p.getId());
        out.println("Nome ....: " + p.getNome());
        out.println("CPF .....: " + p.getCpf());
        out.println("Telefone : " + valor(p.getTelefone()));
        out.println("Cargo ...: " + p.getCargo());
        out.println("Registro : " + p.getRegistroConselho());
    }

    private ProfissionalSaude selecionarProfissional() {
        List<ProfissionalSaude> lista = profissionalService.listar();
        if (lista.isEmpty()) {
            throw new NegocioException("Nenhum profissional cadastrado (opção 3 do menu).");
        }
        out.println("Profissionais:");
        for (int i = 0; i < lista.size(); i++) {
            ProfissionalSaude p = lista.get(i);
            out.printf("  %d - %s (%s, %s)%n", i + 1, p.getNome(), p.getCargo(), p.getRegistroConselho());
        }
        return lista.get(io.lerInt("Profissional: ", 1, lista.size()) - 1);
    }

    // ===================================================================== triagem (RF04/RF05)

    private void novaTriagem() {
        titulo("NOVA TRIAGEM E INSERÇÃO NA FILA");
        Paciente paciente = selecionarPaciente();
        ClassificadorRisco classificador = triagemService.selecionarClassificador(paciente);
        out.printf("Paciente: %s | %s | Protocolo: %s%n", paciente.getNome(), idade(paciente), classificador);

        String unidade = null;
        Optional<Atendimento> ativo = filaService.buscarAtendimentoAtivo(paciente.getId());
        if (ativo.isPresent()) {
            Atendimento a = ativo.get();
            if (a.isTriado() || !Atendimento.STATUS_AGUARDANDO.equals(a.getStatus())) {
                throw new NegocioException("Paciente já possui atendimento ativo (ID " + a.getId() + ", "
                        + a.getStatus() + ", unidade " + a.getCodigoUnidade() + ", " + a.getPrioridade() + ").");
            }
            out.println("Paciente já está na fila da unidade " + a.getCodigoUnidade()
                    + " aguardando triagem; ela será vinculada ao atendimento " + a.getId() + ".");
        } else {
            unidade = selecionarUnidade(true);
        }

        SinaisVitais sinais = lerSinaisVitais();
        Atendimento atendimento = filaService.registrarTriagemEInserirNaFila(paciente.getId(), unidade, sinais);
        int posicao = filaService.obterFilaPriorizada(atendimento.getCodigoUnidade()).posicaoDe(atendimento.getId());

        out.println();
        out.println(LINHA);
        out.println("  CLASSIFICAÇÃO DE RISCO: " + atendimento.getPrioridade());
        out.println(LINHA);
        out.println("  Atendimento ...: " + atendimento.getId());
        out.println("  Unidade .......: " + atendimento.getCodigoUnidade());
        out.println("  Protocolo .....: " + atendimento.getClassificador());
        out.println("  Sinais vitais .: " + atendimento.getSinaisVitais());
        out.println("  Posição na fila: " + (posicao > 0 ? posicao + "º" : "-"));
    }

    private SinaisVitais lerSinaisVitais() {
        out.println("\nSinais vitais:");
        String pressao;
        while (true) {
            pressao = io.lerTexto("  Pressão arterial (ex.: 120/80): ").replace(" ", "");
            if (pressao.matches("\\d{2,3}/\\d{2,3}")) {
                break;
            }
            out.println("  ! Use o formato SISTÓLICA/DIASTÓLICA, ex.: 120/80.");
        }
        int fc = io.lerInt("  Frequência cardíaca (bpm): ", 20, 300);
        double temp = io.lerDouble("  Temperatura (°C): ", 25, 45);
        int fr = io.lerInt("  Frequência respiratória (irpm): ", 4, 120);
        double spo2 = io.lerDouble("  Saturação de O2 (%): ", 30, 100);
        return new SinaisVitais(pressao, fc, temp, fr, spo2);
    }

    private String selecionarUnidade(boolean permitirCadastro) {
        List<String> unidades = filaService.listarUnidades();
        if (unidades.isEmpty() && !permitirCadastro) {
            throw new NegocioException("Nenhuma unidade de saúde cadastrada.");
        }
        out.println("Unidades de saúde:");
        for (int i = 0; i < unidades.size(); i++) {
            out.printf("  %d - %s%n", i + 1, unidades.get(i));
        }
        if (permitirCadastro) {
            out.println("  0 - Cadastrar nova unidade");
        }
        int opcao = io.lerInt("Unidade: ", permitirCadastro ? 0 : 1, unidades.size());
        if (opcao == 0) {
            String codigo = filaService.cadastrarUnidade(io.lerTexto("Código da nova unidade: "));
            sucesso("Unidade " + codigo + " cadastrada.");
            return codigo;
        }
        return unidades.get(opcao - 1);
    }

    // ===================================================================== fila (RF06)

    private void verFila() {
        String unidade = selecionarUnidade(false);
        FilaAtendimento fila = filaService.obterFilaPriorizada(unidade);
        List<Atendimento> emAtendimento = filaService.listarEmAtendimento(unidade);

        titulo("FILA PRIORIZADA - " + unidade + " (" + fila.tamanho() + " aguardando)");
        if (fila.isVazia()) {
            out.println("Nenhum paciente aguardando.");
        } else {
            String formato = "%-4s %-7s %-24s %-28s %-9s %-11s %-17s %-8s%n";
            out.printf(formato, "Pos", "Atend.", "Prioridade", "Paciente", "Idade", "Triagem", "Chegada", "Espera");
            out.println(LINHA);
            int pos = 1;
            for (Atendimento a : fila.consultarFila()) {
                out.printf(formato, pos++ + "º", a.getId(), a.getPrioridade(), cortar(a.getPaciente().getNome(), 28),
                        idade(a.getPaciente()), tipoTriagem(a), DATA_HORA.format(a.getDataHoraChegada()),
                        espera(a.getDataHoraChegada()));
            }
        }

        if (!emAtendimento.isEmpty()) {
            out.println("\nEm atendimento:");
            for (Atendimento a : emAtendimento) {
                out.printf("  #%d %s - %s (com %s)%n", a.getId(), a.getPaciente().getNome(), a.getPrioridade(),
                        a.getProfissional() == null ? "-" : a.getProfissional().getNome());
            }
        }
    }

    // ===================================================================== atendimento (RF07)

    private void menuAtendimento() {
        while (true) {
            titulo("CHAMAR / ATENDER");
            out.println(" 1. Chamar próximo paciente da fila");
            out.println(" 2. Finalizar atendimento em andamento");
            out.println(" 3. Cancelar atendimento (desistência)");
            out.println(" 0. Voltar");
            int opcao = io.lerInt("Opção: ", 0, 3);
            switch (opcao) {
                case 0 -> {
                    return;
                }
                case 1 -> comTratamento(this::chamarProximo);
                case 2 -> comTratamento(this::finalizarAtendimento);
                case 3 -> comTratamento(this::cancelarAtendimento);
                default -> throw new IllegalStateException("Opção inesperada: " + opcao);
            }
        }
    }

    private void chamarProximo() {
        String unidade = selecionarUnidade(false);
        ProfissionalSaude profissional = selecionarProfissional();
        Atendimento a = filaService.chamarProximo(unidade, profissional.getId());

        out.println();
        out.println(LINHA);
        out.printf("  CHAMANDO: %s  ->  %s%n", a.getPaciente().getNome().toUpperCase(), profissional.getNome());
        out.println(LINHA);
        out.println("  Atendimento ...: " + a.getId());
        out.println("  Prioridade ....: " + a.getPrioridade());
        out.println("  Idade .........: " + idade(a.getPaciente()));
        out.println("  Sinais vitais .: " + a.getSinaisVitais());
        out.println("  Aguardou ......: " + espera(a.getDataHoraChegada()));
        out.println("  Status ........: " + a.getStatus());
    }

    private void finalizarAtendimento() {
        String unidade = selecionarUnidade(false);
        List<Atendimento> emAndamento = filaService.listarEmAtendimento(unidade);
        if (emAndamento.isEmpty()) {
            out.println("Nenhum atendimento em andamento na unidade " + unidade + ".");
            return;
        }
        out.println("Atendimentos em andamento:");
        for (int i = 0; i < emAndamento.size(); i++) {
            Atendimento a = emAndamento.get(i);
            out.printf("  %d - #%d %s (com %s)%n", i + 1, a.getId(), a.getPaciente().getNome(),
                    a.getProfissional() == null ? "-" : a.getProfissional().getNome());
        }
        Atendimento escolhido = emAndamento.get(io.lerInt("Finalizar qual? ", 1, emAndamento.size()) - 1);
        Atendimento a = filaService.finalizarAtendimento(escolhido.getId());
        sucesso("Atendimento " + a.getId() + " de " + a.getPaciente().getNome() + " finalizado.");
    }

    private void cancelarAtendimento() {
        int id = io.lerInt("ID do atendimento: ", 1, Integer.MAX_VALUE);
        Atendimento a = filaService.buscarAtendimento(id);
        out.printf("#%d %s - %s - %s - unidade %s%n", a.getId(), a.getPaciente().getNome(), a.getStatus(),
                a.getPrioridade(), a.getCodigoUnidade());
        if (io.confirmar("Confirma o cancelamento?")) {
            filaService.cancelarAtendimento(id);
            sucesso("Atendimento " + id + " cancelado.");
        }
    }

    // ===================================================================== histórico (RF08)

    private void historicoPaciente() {
        Paciente p = pacienteService.consultarHistorico(selecionarPaciente().getId());
        titulo("HISTÓRICO DE ATENDIMENTOS - " + p.getNome() + " (" + p.getCpf() + ")");
        List<Atendimento> historico = p.getHistoricoAtendimentos();
        if (historico.isEmpty()) {
            out.println("Nenhum atendimento registrado.");
            return;
        }
        String formato = "%-6s %-17s %-20s %-24s %-15s %-25s%n";
        out.printf(formato, "ID", "Chegada", "Unidade", "Prioridade", "Status", "Profissional");
        out.println(LINHA);
        for (Atendimento a : historico) {
            out.printf(formato, a.getId(), DATA_HORA.format(a.getDataHoraChegada()), cortar(a.getCodigoUnidade(), 20),
                    a.getPrioridade(), a.getStatus(),
                    a.getProfissional() == null ? "-" : cortar(a.getProfissional().getNome(), 25));
            if (a.isTriado()) {
                out.println("       Triagem " + tipoTriagem(a).toLowerCase() + ": " + a.getSinaisVitais());
            }
        }
        out.println(historico.size() + " atendimento(s).");
    }

    // ===================================================================== helpers de exibição

    private void titulo(String texto) {
        out.println();
        out.println("--- " + texto + " ---");
    }

    private void sucesso(String msg) {
        out.println("\n  [OK] " + msg);
    }

    private void erro(String msg) {
        out.println("\n  [ERRO] " + msg);
    }

    private static String idade(Paciente p) {
        int meses = p.getIdadeEmMeses();
        return meses < 24 ? meses + " meses" : (meses / 12) + " anos";
    }

    private static String tipoTriagem(Atendimento a) {
        if (!a.isTriado()) {
            return "-";
        }
        return a.getClassificador() instanceof br.com.gestaosaude.model.TriagemPediatrica ? "PEDIATRICA" : "ADULTO";
    }

    private static String espera(LocalDateTime chegada) {
        Duration d = Duration.between(chegada, LocalDateTime.now());
        if (d.isNegative()) {
            return "0min";
        }
        long dias = d.toDays();
        if (dias > 0) {
            return dias + "d" + d.toHoursPart() + "h";
        }
        return d.toHours() > 0 ? String.format("%dh%02dmin", d.toHours(), d.toMinutesPart()) : d.toMinutes() + "min";
    }

    private static String cortar(String texto, int max) {
        if (texto == null) {
            return "-";
        }
        return texto.length() <= max ? texto : texto.substring(0, max - 1) + "…";
    }

    private static String valor(String texto) {
        return texto == null || texto.isBlank() ? "-" : texto;
    }

    private static String causaRaiz(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage();
    }
}
