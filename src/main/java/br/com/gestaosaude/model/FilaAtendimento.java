package br.com.gestaosaude.model;

import br.com.gestaosaude.exception.NegocioException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fila de atendimento de uma unidade de saúde.
 * <p>
 * Ordenação: prioridade (Vermelho &gt; Amarelo &gt; Verde &gt; Sem triagem) e,
 * em caso de empate, ordem de chegada.
 */
public class FilaAtendimento {

    public static final Comparator<Atendimento> ORDEM_DE_PRIORIDADE =
            Comparator.comparingInt((Atendimento a) -> a.getNivelPrioridade().getOrdem())
                    .thenComparing(Atendimento::getDataHoraChegada)
                    .thenComparingInt(Atendimento::getId);

    private final String codigoUnidade;
    private final List<Atendimento> atendimentos = new ArrayList<>();

    public FilaAtendimento(String codigoUnidade) {
        if (codigoUnidade == null || codigoUnidade.isBlank()) {
            throw new IllegalArgumentException("Código da unidade é obrigatório.");
        }
        this.codigoUnidade = codigoUnidade;
    }

    public void adicionarAtendimento(Atendimento a) {
        Objects.requireNonNull(a, "Atendimento é obrigatório.");
        if (!Atendimento.STATUS_AGUARDANDO.equals(a.getStatus())) {
            throw new NegocioException("Apenas atendimentos AGUARDANDO podem entrar na fila.");
        }
        if (a.getCodigoUnidade() != null && !codigoUnidade.equals(a.getCodigoUnidade())) {
            throw new NegocioException("Atendimento pertence à unidade " + a.getCodigoUnidade()
                    + ", não à " + codigoUnidade + ".");
        }
        if (a.getId() > 0 && atendimentos.stream().anyMatch(x -> x.getId() == a.getId())) {
            throw new NegocioException("Atendimento " + a.getId() + " já está na fila.");
        }
        a.setCodigoUnidade(codigoUnidade);
        atendimentos.add(a);
        ordenarFilaPorPrioridade();
    }

    /** @return {@code true} se o atendimento estava na fila e foi removido */
    public boolean removerAtendimento(int id) {
        return atendimentos.removeIf(a -> a.getId() == id);
    }

    public void ordenarFilaPorPrioridade() {
        atendimentos.sort(ORDEM_DE_PRIORIDADE);
    }

    /** Retorna um retrato imutável da fila já ordenada. */
    public List<Atendimento> consultarFila() {
        ordenarFilaPorPrioridade();
        return List.copyOf(atendimentos);
    }

    /** Próximo paciente a ser chamado, sem removê-lo da fila. */
    public Optional<Atendimento> proximo() {
        ordenarFilaPorPrioridade();
        return atendimentos.isEmpty() ? Optional.empty() : Optional.of(atendimentos.get(0));
    }

    /** Posição (1-based) do atendimento na fila, ou -1 se não estiver nela. */
    public int posicaoDe(int atendimentoId) {
        List<Atendimento> fila = consultarFila();
        for (int i = 0; i < fila.size(); i++) {
            if (fila.get(i).getId() == atendimentoId) {
                return i + 1;
            }
        }
        return -1;
    }

    public int tamanho() {
        return atendimentos.size();
    }

    public boolean isVazia() {
        return atendimentos.isEmpty();
    }

    public String getCodigoUnidade() {
        return codigoUnidade;
    }
}
