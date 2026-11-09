package br.com.gestaosaude.model;

import java.util.Objects;

/**
 * Classificação de risco pediátrica (menores de 18 anos).
 * <p>
 * Os limites de frequência cardíaca (FC) e respiratória (FR) variam conforme a
 * faixa etária. Protocolo acadêmico simplificado (não substitui protocolo clínico):
 * <pre>
 * VERMELHO: SpO2 &lt; 92% | T &ge; 40 ou &lt; 35,5 °C | febre (&ge; 38 °C) em bebê &lt; 3 meses
 *           | FC &lt; 75% do mínimo ou &gt; 120% do máximo da faixa
 *           | FR &lt; 60% do mínimo ou &gt; 120% do máximo da faixa
 * AMARELO:  SpO2 92–94% | T &ge; 38,5 °C | FC ou FR fora da faixa normal
 * VERDE:    demais casos
 * </pre>
 * A pressão arterial não é usada: seus valores de referência dependem de
 * percentis de altura, fora do escopo desta triagem.
 */
public class TriagemPediatrica implements ClassificadorRisco {

    private int faixaEtariaMeses;

    /** Valores normais de FC/FR por faixa etária. */
    private record FaixaReferencia(int fcMin, int fcMax, int frMin, int frMax) {
    }

    public TriagemPediatrica(int faixaEtariaMeses) {
        setFaixaEtariaMeses(faixaEtariaMeses);
    }

    @Override
    public String calcularPrioridade(SinaisVitais sinais) {
        return classificar(sinais).getDescricao();
    }

    public NivelPrioridade classificar(SinaisVitais s) {
        Objects.requireNonNull(s, "Sinais vitais são obrigatórios.");
        FaixaReferencia ref = faixaReferencia();
        double spo2 = s.getSaturacaoOxigenio();
        double temp = s.getTemperatura();
        int fc = s.getFrequenciaCardiaca();
        int fr = s.getFrequenciaRespiratoria();

        boolean emergencia =
                (spo2 > 0 && spo2 < 92.0)
                || (temp > 0 && (temp >= 40.0 || temp < 35.5))
                || (faixaEtariaMeses < 3 && temp >= 38.0)
                || (fc > 0 && (fc < ref.fcMin() * 0.75 || fc > ref.fcMax() * 1.2))
                || (fr > 0 && (fr < ref.frMin() * 0.6 || fr > ref.frMax() * 1.2));
        if (emergencia) {
            return NivelPrioridade.VERMELHO;
        }

        boolean urgencia =
                (spo2 > 0 && spo2 < 95.0)
                || temp >= 38.5
                || (fc > 0 && (fc < ref.fcMin() || fc > ref.fcMax()))
                || (fr > 0 && (fr < ref.frMin() || fr > ref.frMax()));
        return urgencia ? NivelPrioridade.AMARELO : NivelPrioridade.VERDE;
    }

    private FaixaReferencia faixaReferencia() {
        if (faixaEtariaMeses < 12) {
            return new FaixaReferencia(100, 160, 30, 60);   // lactente
        } else if (faixaEtariaMeses < 36) {
            return new FaixaReferencia(90, 150, 24, 40);    // 1 a 2 anos
        } else if (faixaEtariaMeses < 72) {
            return new FaixaReferencia(80, 140, 22, 34);    // pré-escolar (3 a 5)
        } else if (faixaEtariaMeses < 144) {
            return new FaixaReferencia(70, 120, 18, 30);    // escolar (6 a 11)
        }
        return new FaixaReferencia(60, 100, 12, 20);        // adolescente (12 a 17)
    }

    public int getFaixaEtariaMeses() {
        return faixaEtariaMeses;
    }

    public final void setFaixaEtariaMeses(int faixaEtariaMeses) {
        if (faixaEtariaMeses < 0) {
            throw new IllegalArgumentException("Faixa etária em meses não pode ser negativa.");
        }
        this.faixaEtariaMeses = faixaEtariaMeses;
    }

    @Override
    public String toString() {
        return "Triagem Pediátrica (" + faixaEtariaMeses + " meses)";
    }
}
