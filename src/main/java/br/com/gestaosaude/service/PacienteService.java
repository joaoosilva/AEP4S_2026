package br.com.gestaosaude.service;

import br.com.gestaosaude.dao.AtendimentoDAO;
import br.com.gestaosaude.dao.PacienteDAO;
import br.com.gestaosaude.exception.NegocioException;
import br.com.gestaosaude.model.Paciente;
import br.com.gestaosaude.util.CpfUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * Regras de negócio de pacientes (RF01, RF02 e RF08).
 */
public class PacienteService {

    private static final int IDADE_MAXIMA_ANOS = 130;

    private final PacienteDAO pacienteDAO;
    private final AtendimentoDAO atendimentoDAO;

    public PacienteService(PacienteDAO pacienteDAO, AtendimentoDAO atendimentoDAO) {
        this.pacienteDAO = pacienteDAO;
        this.atendimentoDAO = atendimentoDAO;
    }

    public Paciente cadastrar(Paciente paciente) {
        normalizarEValidar(paciente);
        return pacienteDAO.inserir(paciente);
    }

    public List<Paciente> listar() {
        return pacienteDAO.listar();
    }

    public Paciente buscarPorId(int id) {
        return pacienteDAO.buscarPorId(id)
                .orElseThrow(() -> new NegocioException("Paciente com ID " + id + " não encontrado."));
    }

    public Paciente buscarPorCpf(String cpf) {
        String formatado = CpfUtils.formatar(cpf);
        return pacienteDAO.buscarPorCpf(formatado)
                .orElseThrow(() -> new NegocioException("Paciente com CPF " + formatado + " não encontrado."));
    }

    public void atualizar(Paciente paciente) {
        buscarPorId(paciente.getId());
        normalizarEValidar(paciente);
        pacienteDAO.atualizar(paciente);
    }

    /**
     * Exclui o paciente. Pacientes com histórico de atendimentos não podem ser
     * excluídos, para preservar o registro clínico.
     */
    public void excluir(int id) {
        buscarPorId(id);
        int atendimentos = atendimentoDAO.contarPorPaciente(id);
        if (atendimentos > 0) {
            throw new NegocioException("Paciente possui " + atendimentos
                    + " atendimento(s) no histórico e não pode ser excluído.");
        }
        pacienteDAO.excluir(id);
    }

    /** RF08: retorna o paciente com o histórico de atendimentos carregado (mais recente primeiro). */
    public Paciente consultarHistorico(int pacienteId) {
        Paciente paciente = buscarPorId(pacienteId);
        paciente.setHistoricoAtendimentos(atendimentoDAO.listarPorPaciente(pacienteId));
        return paciente;
    }

    private void normalizarEValidar(Paciente p) {
        p.setNome(Validacoes.textoObrigatorio(p.getNome(), "Nome", 150));
        p.setCpf(Validacoes.cpf(p.getCpf()));
        p.setTelefone(Validacoes.textoOpcional(p.getTelefone(), "Telefone", 20));

        String cartao = p.getCartaoSus() == null ? "" : p.getCartaoSus().replaceAll("\\D", "");
        if (cartao.length() != 15) {
            throw new NegocioException("Cartão SUS deve conter 15 dígitos.");
        }
        p.setCartaoSus(cartao);

        LocalDate nascimento = p.getDataNascimento();
        if (nascimento == null) {
            throw new NegocioException("Data de nascimento é obrigatória.");
        }
        if (nascimento.isAfter(LocalDate.now())) {
            throw new NegocioException("Data de nascimento não pode estar no futuro.");
        }
        if (nascimento.isBefore(LocalDate.now().minusYears(IDADE_MAXIMA_ANOS))) {
            throw new NegocioException("Data de nascimento inválida.");
        }
    }
}
