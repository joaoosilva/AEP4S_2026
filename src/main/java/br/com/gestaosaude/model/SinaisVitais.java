package br.com.gestaosaude.model;

/**
 * Sinais vitais aferidos na triagem.
 * <p>
 * Convenção: valores numéricos {@code 0} (ou pressão {@code null}) significam
 * "não aferido" e são ignorados pelos classificadores de risco.
 */
public class SinaisVitais {

    private String pressaoArterial;       // formato "SISTOLICA/DIASTOLICA", ex.: "120/80"
    private int frequenciaCardiaca;       // bpm
    private double temperatura;           // °C
    private int frequenciaRespiratoria;   // incursões por minuto
    private double saturacaoOxigenio;     // % SpO2

    public SinaisVitais() {
    }

    public SinaisVitais(String pressaoArterial, int frequenciaCardiaca, double temperatura,
                        int frequenciaRespiratoria, double saturacaoOxigenio) {
        this.pressaoArterial = pressaoArterial;
        this.frequenciaCardiaca = frequenciaCardiaca;
        this.temperatura = temperatura;
        this.frequenciaRespiratoria = frequenciaRespiratoria;
        this.saturacaoOxigenio = saturacaoOxigenio;
    }

    /** Pressão sistólica em mmHg, ou 0 se não informada/ilegível. */
    public int getPressaoSistolica() {
        return parteDaPressao(0);
    }

    /** Pressão diastólica em mmHg, ou 0 se não informada/ilegível. */
    public int getPressaoDiastolica() {
        return parteDaPressao(1);
    }

    private int parteDaPressao(int indice) {
        if (pressaoArterial == null) {
            return 0;
        }
        String[] partes = pressaoArterial.trim().split("/");
        if (partes.length != 2) {
            return 0;
        }
        try {
            return Integer.parseInt(partes[indice].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getPressaoArterial() {
        return pressaoArterial;
    }

    public void setPressaoArterial(String pressaoArterial) {
        this.pressaoArterial = pressaoArterial;
    }

    public int getFrequenciaCardiaca() {
        return frequenciaCardiaca;
    }

    public void setFrequenciaCardiaca(int frequenciaCardiaca) {
        this.frequenciaCardiaca = frequenciaCardiaca;
    }

    public double getTemperatura() {
        return temperatura;
    }

    public void setTemperatura(double temperatura) {
        this.temperatura = temperatura;
    }

    public int getFrequenciaRespiratoria() {
        return frequenciaRespiratoria;
    }

    public void setFrequenciaRespiratoria(int frequenciaRespiratoria) {
        this.frequenciaRespiratoria = frequenciaRespiratoria;
    }

    public double getSaturacaoOxigenio() {
        return saturacaoOxigenio;
    }

    public void setSaturacaoOxigenio(double saturacaoOxigenio) {
        this.saturacaoOxigenio = saturacaoOxigenio;
    }

    @Override
    public String toString() {
        return String.format("PA %s mmHg | FC %d bpm | T %.1f °C | FR %d irpm | SpO2 %.1f%%",
                pressaoArterial == null ? "-" : pressaoArterial,
                frequenciaCardiaca, temperatura, frequenciaRespiratoria, saturacaoOxigenio);
    }
}
