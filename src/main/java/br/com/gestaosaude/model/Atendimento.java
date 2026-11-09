package br.com.gestaosaude.model;

import br.com.gestaosaude.exception.NegocioException;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Passagem de um paciente pela fila de uma unidade de saúde.
 * <p>
 * Ciclo de vida: {@code AGUARDANDO → EM_ATENDIMENTO → FINALIZADO};
 * {@code CANCELADO} pode ocorrer a partir de qualquer status não final.
 */
public class Atendimento {

    public static final String STATUS_AGUARDANDO = "AGUARDANDO";
    public static final String STATUS_EM_ATENDIMENTO = "EM_ATENDIMENTO";
    public static final String STATUS_FINALIZADO = "FINALIZADO";
    public static final String STATUS_CANCELADO = "CANCELADO";

    private int id;
    private LocalDateTime dataHoraChegada;
    private String status;
    private Paciente paciente;
    private ProfissionalSaude profissional;
    private ClassificadorRisco classificador;
    private SinaisVitais sinaisVitais;
    private String prioridadeCalculada;
    private Integer triagemId;
    private String codigoUnidade;

    public Atendimento() {
        this.dataHoraChegada = LocalDateTime.now();
        this.status = STATUS_AGUARDANDO;
    }

    public Atendimento(Paciente paciente, String codigoUnidade) {
        this();
        this.paciente = Objects.requireNonNull(paciente, "Paciente é obrigatório.");
        this.codigoUnidade = Objects.requireNonNull(codigoUnidade, "Unidade é obrigatória.");
    }

    /**
     * Aplica a triagem: guarda classificador e sinais vitais e calcula a prioridade.
     *
     * @return prioridade calculada
     */
    public String registrarTriagem(ClassificadorRisco classificador, SinaisVitais sinais) {
        Objects.requireNonNull(classificador, "Classificador de risco é obrigatório.");
        Objects.requireNonNull(sinais, "Sinais vitais são obrigatórios.");
        if (!STATUS_AGUARDANDO.equals(status)) {
            throw new NegocioException("Triagem só pode ser registrada para atendimentos aguardando.");
        }
        this.classificador = classificador;
        this.sinaisVitais = sinais;
        this.prioridadeCalculada = classificador.calcularPrioridade(sinais);
        return prioridadeCalculada;
    }

    /** Inicia o atendimento com o profissional já atribuído. */
    public void iniciarAtendimento() {
        if (!STATUS_AGUARDANDO.equals(status)) {
            throw new NegocioException("Só é possível iniciar atendimentos com status AGUARDANDO (atual: "
                    + status + ").");
        }
        if (profissional == null) {
            throw new NegocioException("Informe o profissional responsável antes de iniciar o atendimento.");
        }
        this.status = STATUS_EM_ATENDIMENTO;
    }

    /** Atribui o profissional e inicia o atendimento. */
    public void iniciarAtendimento(ProfissionalSaude profissional) {
        Objects.requireNonNull(profissional, "Profissional é obrigatório.");
        if (!STATUS_AGUARDANDO.equals(status)) {
            throw new NegocioException("Só é possível iniciar atendimentos com status AGUARDANDO (atual: "
                    + status + ").");
        }
        this.profissional = profissional;
        iniciarAtendimento();
    }

    public void finalizarAtendimento() {
        if (!STATUS_EM_ATENDIMENTO.equals(status)) {
            throw new NegocioException("Só é possível finalizar atendimentos EM_ATENDIMENTO (atual: "
                    + status + ").");
        }
        this.status = STATUS_FINALIZADO;
    }

    public void cancelarAtendimento() {
        if (STATUS_FINALIZADO.equals(status) || STATUS_CANCELADO.equals(status)) {
            throw new NegocioException("Atendimento já encerrado (status: " + status + ").");
        }
        this.status = STATUS_CANCELADO;
    }

    /**
     * Prioridade calculada pela triagem; se ainda não calculada mas houver dados de triagem,
     * calcula sob demanda. Sem triagem, retorna {@link NivelPrioridade#NAO_CLASSIFICADO}.
     */
    public String getPrioridade() {
        if (prioridadeCalculada == null && classificador != null && sinaisVitais != null) {
            prioridadeCalculada = classificador.calcularPrioridade(sinaisVitais);
        }
        return prioridadeCalculada != null ? prioridadeCalculada : NivelPrioridade.NAO_CLASSIFICADO.getDescricao();
    }

    public NivelPrioridade getNivelPrioridade() {
        return NivelPrioridade.fromDescricao(getPrioridade());
    }

    public boolean isTriado() {
        return classificador != null && sinaisVitais != null;
    }

    public boolean isAtivo() {
        return STATUS_AGUARDANDO.equals(status) || STATUS_EM_ATENDIMENTO.equals(status);
    }

    // ---------------------------------------------------------------- getters/setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public LocalDateTime getDataHoraChegada() {
        return dataHoraChegada;
    }

    public void setDataHoraChegada(LocalDateTime dataHoraChegada) {
        this.dataHoraChegada = dataHoraChegada;
    }

    public String getStatus() {
        return status;
    }

    /** Uso pela camada de persistência; regras de transição ficam nos métodos de negócio. */
    public void setStatus(String status) {
        this.status = status;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public void setPaciente(Paciente paciente) {
        this.paciente = paciente;
    }

    public ProfissionalSaude getProfissional() {
        return profissional;
    }

    public void setProfissional(ProfissionalSaude profissional) {
        this.profissional = profissional;
    }

    public ClassificadorRisco getClassificador() {
        return classificador;
    }

    public void setClassificador(ClassificadorRisco classificador) {
        this.classificador = classificador;
        this.prioridadeCalculada = null;
    }

    public SinaisVitais getSinaisVitais() {
        return sinaisVitais;
    }

    public void setSinaisVitais(SinaisVitais sinaisVitais) {
        this.sinaisVitais = sinaisVitais;
        this.prioridadeCalculada = null;
    }

    public String getPrioridadeCalculada() {
        return prioridadeCalculada;
    }

    public Integer getTriagemId() {
        return triagemId;
    }

    public void setTriagemId(Integer triagemId) {
        this.triagemId = triagemId;
    }

    public String getCodigoUnidade() {
        return codigoUnidade;
    }

    public void setCodigoUnidade(String codigoUnidade) {
        this.codigoUnidade = codigoUnidade;
    }

    @Override
    public String toString() {
        return "Atendimento[id=" + id + ", status=" + status + ", prioridade=" + getPrioridade()
                + ", paciente=" + (paciente == null ? "-" : paciente.getNome()) + "]";
    }
}
