package br.com.gestaosaude.model;

/**
 * Estratégia de classificação de risco (padrão Strategy).
 * Cada implementação aplica um protocolo diferente sobre os mesmos sinais vitais.
 */
public interface ClassificadorRisco {

    /**
     * Calcula a prioridade de atendimento.
     *
     * @param sinais sinais vitais aferidos na triagem
     * @return descrição da prioridade, ex.: {@code "VERMELHO - EMERGÊNCIA"}
     *         (ver {@link NivelPrioridade#getDescricao()})
     */
    String calcularPrioridade(SinaisVitais sinais);
}
