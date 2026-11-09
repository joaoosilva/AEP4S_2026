package br.com.gestaosaude.service;

import br.com.gestaosaude.exception.NegocioException;
import br.com.gestaosaude.model.ClassificadorRisco;
import br.com.gestaosaude.model.Paciente;
import br.com.gestaosaude.model.SinaisVitais;
import br.com.gestaosaude.model.TriagemAdulto;
import br.com.gestaosaude.model.TriagemPediatrica;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regras de triagem (RF04 e RF05): validação dos sinais vitais e escolha
 * polimórfica do classificador de risco conforme a idade do paciente.
 */
public class TriagemService {

    private static final Pattern PRESSAO = Pattern.compile("^\\s*(\\d{2,3})\\s*/\\s*(\\d{2,3})\\s*$");

    /** Pacientes com menos de 18 anos recebem {@link TriagemPediatrica}; os demais, {@link TriagemAdulto}. */
    public ClassificadorRisco selecionarClassificador(Paciente paciente) {
        Objects.requireNonNull(paciente, "Paciente é obrigatório.");
        if (paciente.isPediatrico()) {
            return new TriagemPediatrica(paciente.getIdadeEmMeses());
        }
        return new TriagemAdulto();
    }

    /** Valida faixas fisiologicamente possíveis e normaliza a pressão para "SIS/DIA". */
    public void validarSinais(SinaisVitais s) {
        Objects.requireNonNull(s, "Sinais vitais são obrigatórios.");

        Matcher m = PRESSAO.matcher(s.getPressaoArterial() == null ? "" : s.getPressaoArterial());
        if (!m.matches()) {
            throw new NegocioException("Pressão arterial deve estar no formato SISTÓLICA/DIASTÓLICA, ex.: 120/80.");
        }
        int sistolica = Integer.parseInt(m.group(1));
        int diastolica = Integer.parseInt(m.group(2));
        if (sistolica <= diastolica) {
            throw new NegocioException("Pressão sistólica deve ser maior que a diastólica.");
        }
        s.setPressaoArterial(sistolica + "/" + diastolica);

        faixa(s.getFrequenciaCardiaca(), 20, 300, "Frequência cardíaca (bpm)");
        faixa(s.getTemperatura(), 25.0, 45.0, "Temperatura (°C)");
        faixa(s.getFrequenciaRespiratoria(), 4, 120, "Frequência respiratória (irpm)");
        faixa(s.getSaturacaoOxigenio(), 30.0, 100.0, "Saturação de O2 (%)");
    }

    private static void faixa(double valor, double min, double max, String campo) {
        if (valor < min || valor > max) {
            throw new NegocioException(String.format("%s deve estar entre %s e %s.",
                    campo, formatar(min), formatar(max)));
        }
    }

    private static String formatar(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
