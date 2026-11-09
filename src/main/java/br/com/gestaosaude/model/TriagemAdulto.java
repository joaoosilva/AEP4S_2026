package br.com.gestaosaude.model;

import java.util.Objects;

/**
 * Classificação de risco para pacientes adultos (18 anos ou mais).
 * <p>
 * Protocolo acadêmico simplificado (não substitui protocolo clínico validado):
 * <pre>
 * VERMELHO: SpO2 &lt; 90% | T &ge; 41 ou &lt; 35 °C | FC &lt; 40 ou &gt; 130 | FR &lt; 8 ou &gt; 30
 *           | PAS &lt; 90 (hipotensão) ou &ge; 180 | PAD &ge; 120
 * AMARELO:  SpO2 90–94% | T &ge; 39 °C (febre alta) | FC &lt; 50 ou &gt; 110 | FR &lt; 10 ou &gt; 24
 *           | PAS &ge; 160 | PAD &ge; 100
 * VERDE:    demais casos
 * </pre>
 */
public class TriagemAdulto implements ClassificadorRisco {

    public static final double SPO2_CRITICA = 90.0;
    public static final double SPO2_BAIXA = 95.0;
    public static final double FEBRE_ALTA = 39.0;
    public static final double HIPERTERMIA_GRAVE = 41.0;
    public static final double HIPOTERMIA = 35.0;

    @Override
    public String calcularPrioridade(SinaisVitais sinais) {
        return classificar(sinais).getDescricao();
    }

    public NivelPrioridade classificar(SinaisVitais s) {
        Objects.requireNonNull(s, "Sinais vitais são obrigatórios.");
        double spo2 = s.getSaturacaoOxigenio();
        double temp = s.getTemperatura();
        int fc = s.getFrequenciaCardiaca();
        int fr = s.getFrequenciaRespiratoria();
        int pas = s.getPressaoSistolica();
        int pad = s.getPressaoDiastolica();

        boolean emergencia =
                (spo2 > 0 && spo2 < SPO2_CRITICA)
                || (temp > 0 && (temp >= HIPERTERMIA_GRAVE || temp < HIPOTERMIA))
                || (fc > 0 && (fc < 40 || fc > 130))
                || (fr > 0 && (fr < 8 || fr > 30))
                || (pas > 0 && (pas < 90 || pas >= 180))
                || (pad >= 120);
        if (emergencia) {
            return NivelPrioridade.VERMELHO;
        }

        boolean urgencia =
                (spo2 > 0 && spo2 < SPO2_BAIXA)
                || temp >= FEBRE_ALTA
                || (fc > 0 && (fc < 50 || fc > 110))
                || (fr > 0 && (fr < 10 || fr > 24))
                || pas >= 160
                || pad >= 100;
        return urgencia ? NivelPrioridade.AMARELO : NivelPrioridade.VERDE;
    }

    @Override
    public String toString() {
        return "Triagem Adulto";
    }
}
