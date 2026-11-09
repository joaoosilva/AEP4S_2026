package br.com.gestaosaude.model;

/**
 * Superclasse abstrata de todas as pessoas do sistema (generalização do DER).
 */
public abstract class Pessoa {

    private int id;
    private String nome;
    private String cpf;
    private String telefone;

    protected Pessoa() {
    }

    protected Pessoa(String nome, String cpf, String telefone) {
        this.nome = nome;
        this.cpf = cpf;
        this.telefone = telefone;
    }

    protected Pessoa(int id, String nome, String cpf, String telefone) {
        this(nome, cpf, telefone);
        this.id = id;
    }

    /** Descrição do papel da pessoa no sistema (polimórfico). */
    public abstract String getTipo();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    @Override
    public String toString() {
        return getTipo() + "[id=" + id + ", nome=" + nome + ", cpf=" + cpf + "]";
    }
}
