package br.com.gestaosaude.model;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Paciente atendido nas unidades de saúde.
 */
public class Paciente extends Pessoa {

    /** Idade (em anos) a partir da qual se aplica a triagem de adulto. */
    public static final int IDADE_ADULTO_ANOS = 18;

    private String cartaoSus;
    private LocalDate dataNascimento;
    private final List<Atendimento> historicoAtendimentos = new ArrayList<>();

    public Paciente() {
    }

    public Paciente(String nome, String cpf, String telefone, String cartaoSus, LocalDate dataNascimento) {
        super(nome, cpf, telefone);
        this.cartaoSus = cartaoSus;
        this.dataNascimento = dataNascimento;
    }

    public Paciente(int id, String nome, String cpf, String telefone, String cartaoSus, LocalDate dataNascimento) {
        super(id, nome, cpf, telefone);
        this.cartaoSus = cartaoSus;
        this.dataNascimento = dataNascimento;
    }

    @Override
    public String getTipo() {
        return "Paciente";
    }

    /** Idade completa em meses na data atual. */
    public int getIdadeEmMeses() {
        return (int) Period.between(dataNascimento, LocalDate.now()).toTotalMonths();
    }

    public int getIdadeEmAnos() {
        return Period.between(dataNascimento, LocalDate.now()).getYears();
    }

    /** Pacientes com menos de 18 anos recebem triagem pediátrica. */
    public boolean isPediatrico() {
        return getIdadeEmAnos() < IDADE_ADULTO_ANOS;
    }

    public String getCartaoSus() {
        return cartaoSus;
    }

    public void setCartaoSus(String cartaoSus) {
        this.cartaoSus = cartaoSus;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(LocalDate dataNascimento) {
        this.dataNascimento = dataNascimento;
    }

    /** Histórico somente leitura; use {@link #adicionarAoHistorico} para incluir itens. */
    public List<Atendimento> getHistoricoAtendimentos() {
        return Collections.unmodifiableList(historicoAtendimentos);
    }

    public void adicionarAoHistorico(Atendimento atendimento) {
        historicoAtendimentos.add(atendimento);
    }

    public void setHistoricoAtendimentos(List<Atendimento> atendimentos) {
        historicoAtendimentos.clear();
        historicoAtendimentos.addAll(atendimentos);
    }
}
