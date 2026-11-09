package br.com.gestaosaude.model;

/**
 * Níveis de prioridade (inspirados no Protocolo de Manchester, simplificado em 3 cores).
 * {@code ordem} menor = atendido primeiro.
 */
public enum NivelPrioridade {

    VERMELHO("VERMELHO - EMERGÊNCIA", 1),
    AMARELO("AMARELO - URGÊNCIA", 2),
    VERDE("VERDE - POUCO URGENTE", 3),
    /** Atendimento registrado na fila mas ainda sem triagem. */
    NAO_CLASSIFICADO("SEM TRIAGEM", 4);

    private final String descricao;
    private final int ordem;

    NivelPrioridade(String descricao, int ordem) {
        this.descricao = descricao;
        this.ordem = ordem;
    }

    public String getDescricao() {
        return descricao;
    }

    public int getOrdem() {
        return ordem;
    }

    /**
     * Converte a descrição retornada por {@link ClassificadorRisco#calcularPrioridade}
     * de volta para o nível. Aceita também apenas o nome da cor (ex.: "AMARELO").
     */
    public static NivelPrioridade fromDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return NAO_CLASSIFICADO;
        }
        String valor = descricao.trim().toUpperCase();
        for (NivelPrioridade nivel : values()) {
            if (nivel.descricao.equals(valor) || valor.startsWith(nivel.name())) {
                return nivel;
            }
        }
        return NAO_CLASSIFICADO;
    }
}
