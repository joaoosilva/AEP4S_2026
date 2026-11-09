package br.com.gestaosaude.model;

/**
 * Profissional de saúde (Médico, Enfermeiro etc.) que realiza triagens e atendimentos.
 */
public class ProfissionalSaude extends Pessoa {

    private String registroConselho;
    private String cargo;

    public ProfissionalSaude() {
    }

    public ProfissionalSaude(String nome, String cpf, String telefone, String registroConselho, String cargo) {
        super(nome, cpf, telefone);
        this.registroConselho = registroConselho;
        this.cargo = cargo;
    }

    public ProfissionalSaude(int id, String nome, String cpf, String telefone,
                             String registroConselho, String cargo) {
        super(id, nome, cpf, telefone);
        this.registroConselho = registroConselho;
        this.cargo = cargo;
    }

    @Override
    public String getTipo() {
        return "Profissional de Saúde";
    }

    public String getRegistroConselho() {
        return registroConselho;
    }

    public void setRegistroConselho(String registroConselho) {
        this.registroConselho = registroConselho;
    }

    public String getCargo() {
        return cargo;
    }

    public void setCargo(String cargo) {
        this.cargo = cargo;
    }
}
