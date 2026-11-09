package br.com.gestaosaude.service;

import br.com.gestaosaude.dao.ProfissionalSaudeDAO;
import br.com.gestaosaude.exception.NegocioException;
import br.com.gestaosaude.model.ProfissionalSaude;
import br.com.gestaosaude.util.CpfUtils;

import java.util.List;

/**
 * Regras de negócio de profissionais de saúde (RF03).
 */
public class ProfissionalSaudeService {

    private final ProfissionalSaudeDAO profissionalDAO;

    public ProfissionalSaudeService(ProfissionalSaudeDAO profissionalDAO) {
        this.profissionalDAO = profissionalDAO;
    }

    public ProfissionalSaude cadastrar(ProfissionalSaude prof) {
        normalizarEValidar(prof);
        return profissionalDAO.inserir(prof);
    }

    public List<ProfissionalSaude> listar() {
        return profissionalDAO.listar();
    }

    public ProfissionalSaude buscarPorId(int id) {
        return profissionalDAO.buscarPorId(id)
                .orElseThrow(() -> new NegocioException("Profissional com ID " + id + " não encontrado."));
    }

    public ProfissionalSaude buscarPorCpf(String cpf) {
        String formatado = CpfUtils.formatar(cpf);
        return profissionalDAO.buscarPorCpf(formatado)
                .orElseThrow(() -> new NegocioException("Profissional com CPF " + formatado + " não encontrado."));
    }

    public ProfissionalSaude buscarPorRegistro(String registro) {
        String r = registro == null ? "" : registro.trim();
        return profissionalDAO.buscarPorRegistro(r)
                .orElseThrow(() -> new NegocioException("Profissional com registro " + r + " não encontrado."));
    }

    public void atualizar(ProfissionalSaude prof) {
        buscarPorId(prof.getId());
        normalizarEValidar(prof);
        profissionalDAO.atualizar(prof);
    }

    public void excluir(int id) {
        buscarPorId(id);
        profissionalDAO.excluir(id);
    }

    private void normalizarEValidar(ProfissionalSaude p) {
        p.setNome(Validacoes.textoObrigatorio(p.getNome(), "Nome", 150));
        p.setCpf(Validacoes.cpf(p.getCpf()));
        p.setTelefone(Validacoes.textoOpcional(p.getTelefone(), "Telefone", 20));
        p.setRegistroConselho(Validacoes.textoObrigatorio(p.getRegistroConselho(), "Registro no conselho", 50)
                .toUpperCase());
        p.setCargo(Validacoes.textoObrigatorio(p.getCargo(), "Cargo", 50));
    }
}
