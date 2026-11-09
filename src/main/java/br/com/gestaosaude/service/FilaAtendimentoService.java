package br.com.gestaosaude.service;

import br.com.gestaosaude.dao.AtendimentoDAO;
import br.com.gestaosaude.dao.FilaAtendimentoDAO;
import br.com.gestaosaude.exception.NegocioException;
import br.com.gestaosaude.model.Atendimento;
import br.com.gestaosaude.model.ClassificadorRisco;
import br.com.gestaosaude.model.FilaAtendimento;
import br.com.gestaosaude.model.Paciente;
import br.com.gestaosaude.model.ProfissionalSaude;
import br.com.gestaosaude.model.SinaisVitais;

import java.util.List;
import java.util.Optional;

/**
 * Gerenciamento das filas e do ciclo de vida dos atendimentos (RF04 a RF07).
 * <p>
 * O banco de dados é a fonte da verdade: a {@link FilaAtendimento} é montada a
 * cada consulta, garantindo uma visão sempre atualizada mesmo com vários terminais.
 */
public class FilaAtendimentoService {

    private final AtendimentoDAO atendimentoDAO;
    private final FilaAtendimentoDAO filaDAO;
    private final PacienteService pacienteService;
    private final ProfissionalSaudeService profissionalService;
    private final TriagemService triagemService;

    public FilaAtendimentoService(AtendimentoDAO atendimentoDAO, FilaAtendimentoDAO filaDAO,
                                  PacienteService pacienteService,
                                  ProfissionalSaudeService profissionalService,
                                  TriagemService triagemService) {
        this.atendimentoDAO = atendimentoDAO;
        this.filaDAO = filaDAO;
        this.pacienteService = pacienteService;
        this.profissionalService = profissionalService;
        this.triagemService = triagemService;
    }

    // ---------------------------------------------------------------- unidades

    public List<String> listarUnidades() {
        return filaDAO.listarCodigos();
    }

    /** @return o código normalizado (maiúsculas) efetivamente gravado */
    public String cadastrarUnidade(String codigoUnidade) {
        String codigo = Validacoes.textoObrigatorio(codigoUnidade, "Código da unidade", 50).toUpperCase();
        if (filaDAO.existe(codigo)) {
            throw new NegocioException("Unidade " + codigo + " já cadastrada.");
        }
        filaDAO.inserir(codigo);
        return codigo;
    }

    private void exigirUnidade(String codigoUnidade) {
        if (codigoUnidade == null || !filaDAO.existe(codigoUnidade)) {
            throw new NegocioException("Unidade " + codigoUnidade + " não encontrada.");
        }
    }

    // ---------------------------------------------------------------- triagem (RF04/RF05)

    /** Atendimento ativo (AGUARDANDO/EM_ATENDIMENTO) do paciente, se houver. */
    public Optional<Atendimento> buscarAtendimentoAtivo(int pacienteId) {
        return atendimentoDAO.buscarAtivoPorPaciente(pacienteId);
    }

    /**
     * Realiza a triagem e coloca o paciente na fila.
     * <ul>
     *   <li>Se o paciente já estiver na fila <b>sem triagem</b>, a triagem é vinculada a esse
     *       atendimento (a unidade informada é ignorada).</li>
     *   <li>Se já houver atendimento ativo triado, a operação é recusada.</li>
     *   <li>Caso contrário, cria um novo atendimento na unidade informada.</li>
     * </ul>
     */
    public Atendimento registrarTriagemEInserirNaFila(int pacienteId, String codigoUnidade, SinaisVitais sinais) {
        Paciente paciente = pacienteService.buscarPorId(pacienteId);
        triagemService.validarSinais(sinais);
        ClassificadorRisco classificador = triagemService.selecionarClassificador(paciente);

        Optional<Atendimento> ativo = atendimentoDAO.buscarAtivoPorPaciente(pacienteId);
        if (ativo.isPresent()) {
            Atendimento existente = ativo.get();
            if (existente.isTriado() || !Atendimento.STATUS_AGUARDANDO.equals(existente.getStatus())) {
                throw new NegocioException("Paciente já possui atendimento ativo (ID " + existente.getId()
                        + ", " + existente.getStatus() + ", unidade " + existente.getCodigoUnidade() + ").");
            }
            existente.registrarTriagem(classificador, sinais);
            atendimentoDAO.vincularTriagem(existente);
            return existente;
        }

        exigirUnidade(codigoUnidade);
        Atendimento novo = new Atendimento(paciente, codigoUnidade);
        novo.registrarTriagem(classificador, sinais);
        return atendimentoDAO.inserir(novo);
    }

    // ---------------------------------------------------------------- fila (RF06)

    public FilaAtendimento obterFilaPriorizada(String codigoUnidade) {
        exigirUnidade(codigoUnidade);
        FilaAtendimento fila = new FilaAtendimento(codigoUnidade);
        for (Atendimento a : atendimentoDAO.listarPorUnidadeEStatus(codigoUnidade, Atendimento.STATUS_AGUARDANDO)) {
            fila.adicionarAtendimento(a);
        }
        fila.ordenarFilaPorPrioridade();
        return fila;
    }

    public List<Atendimento> listarEmAtendimento(String codigoUnidade) {
        exigirUnidade(codigoUnidade);
        return atendimentoDAO.listarPorUnidadeEStatus(codigoUnidade, Atendimento.STATUS_EM_ATENDIMENTO);
    }

    // ---------------------------------------------------------------- atendimento (RF07)

    /**
     * Chama o próximo paciente da fila (maior prioridade / chegada mais antiga)
     * e inicia o atendimento com o profissional informado.
     */
    public Atendimento chamarProximo(String codigoUnidade, int profissionalId) {
        ProfissionalSaude profissional = profissionalService.buscarPorId(profissionalId);
        atendimentoDAO.buscarEmAtendimentoPorProfissional(profissionalId).ifPresent(a -> {
            throw new NegocioException(profissional.getNome() + " já está atendendo "
                    + a.getPaciente().getNome() + " (atendimento " + a.getId() + "). Finalize-o primeiro.");
        });

        FilaAtendimento fila = obterFilaPriorizada(codigoUnidade);
        Atendimento proximo = fila.proximo()
                .orElseThrow(() -> new NegocioException("Não há pacientes aguardando na unidade " + codigoUnidade + "."));
        if (!proximo.isTriado()) {
            throw new NegocioException("O próximo paciente (" + proximo.getPaciente().getNome()
                    + ") ainda não foi triado. Realize a triagem (opção 4) antes de chamá-lo.");
        }

        proximo.iniciarAtendimento(profissional);
        if (!atendimentoDAO.atualizarStatus(proximo, Atendimento.STATUS_AGUARDANDO)) {
            throw new NegocioException("O atendimento " + proximo.getId()
                    + " foi chamado em outro terminal. Tente novamente.");
        }
        fila.removerAtendimento(proximo.getId());
        return proximo;
    }

    public Atendimento finalizarAtendimento(int atendimentoId) {
        Atendimento a = buscarAtendimento(atendimentoId);
        a.finalizarAtendimento();
        if (!atendimentoDAO.atualizarStatus(a, Atendimento.STATUS_EM_ATENDIMENTO)) {
            throw new NegocioException("O atendimento " + atendimentoId + " foi alterado por outro usuário.");
        }
        return a;
    }

    public Atendimento cancelarAtendimento(int atendimentoId) {
        Atendimento a = buscarAtendimento(atendimentoId);
        String statusAnterior = a.getStatus();
        a.cancelarAtendimento();
        if (!atendimentoDAO.atualizarStatus(a, statusAnterior)) {
            throw new NegocioException("O atendimento " + atendimentoId + " foi alterado por outro usuário.");
        }
        return a;
    }

    public Atendimento buscarAtendimento(int atendimentoId) {
        return atendimentoDAO.buscarPorId(atendimentoId)
                .orElseThrow(() -> new NegocioException("Atendimento " + atendimentoId + " não encontrado."));
    }
}
