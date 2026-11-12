-- =============================================================================
-- Sistema de Gestão de Filas e Priorização para Unidades de Saúde
-- Script DDL + DML de teste
-- Compatível com: MySQL 8.0.16+ (CHECK constraints são aplicadas a partir da 8.0.16)
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP DATABASE IF EXISTS gestao_saude_db;
CREATE DATABASE gestao_saude_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE gestao_saude_db;

SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------------------------------
-- Tabela: pessoa (superclasse de paciente e profissional_saude)
-- -----------------------------------------------------------------------------
CREATE TABLE pessoa (
    id        INT          NOT NULL AUTO_INCREMENT,
    nome      VARCHAR(150) NOT NULL,
    cpf       VARCHAR(14)  NOT NULL,
    telefone  VARCHAR(20)  NULL,
    CONSTRAINT pk_pessoa     PRIMARY KEY (id),
    -- O índice UNIQUE também atende às buscas frequentes por CPF
    CONSTRAINT uk_pessoa_cpf UNIQUE (cpf)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- Tabela: paciente (especialização de pessoa, relação 1:1)
-- -----------------------------------------------------------------------------
CREATE TABLE paciente (
    id_pessoa       INT         NOT NULL,
    cartaoSus       VARCHAR(15) NOT NULL,
    dataNascimento  DATE        NOT NULL,
    CONSTRAINT pk_paciente           PRIMARY KEY (id_pessoa),
    CONSTRAINT uk_paciente_cartaoSus UNIQUE (cartaoSus),
    CONSTRAINT fk_paciente_pessoa    FOREIGN KEY (id_pessoa)
        REFERENCES pessoa (id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- Tabela: profissional_saude (especialização de pessoa, relação 1:1)
-- -----------------------------------------------------------------------------
CREATE TABLE profissional_saude (
    id_pessoa         INT         NOT NULL,
    registroConselho  VARCHAR(50) NOT NULL,
    cargo             VARCHAR(50) NOT NULL,           -- ex.: 'Médico', 'Enfermeiro'
    CONSTRAINT pk_profissional_saude         PRIMARY KEY (id_pessoa),
    CONSTRAINT uk_profissional_registro      UNIQUE (registroConselho),
    CONSTRAINT fk_profissional_saude_pessoa  FOREIGN KEY (id_pessoa)
        REFERENCES pessoa (id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- Tabela: fila_atendimento (uma fila por unidade de saúde)
-- -----------------------------------------------------------------------------
CREATE TABLE fila_atendimento (
    codigoUnidade  VARCHAR(50) NOT NULL,
    CONSTRAINT pk_fila_atendimento PRIMARY KEY (codigoUnidade)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- Tabela: triagem (ADULTO ou PEDIATRICA - herança em tabela única)
-- -----------------------------------------------------------------------------
CREATE TABLE triagem (
    id                      INT          NOT NULL AUTO_INCREMENT,
    tipo_triagem            VARCHAR(20)  NOT NULL,
    pressaoArterial         VARCHAR(20)  NULL,        -- ex.: '120/80'
    frequenciaCardiaca      INT          NULL,        -- bpm
    temperatura             DECIMAL(4,2) NULL,        -- °C
    frequenciaRespiratoria  INT          NULL,        -- irpm
    saturacaoOxigenio       DECIMAL(5,2) NULL,        -- % SpO2 (5,2 para aceitar 100.00)
    faixaEtariaMeses        INT          NULL,        -- obrigatório apenas p/ PEDIATRICA
    CONSTRAINT pk_triagem PRIMARY KEY (id),
    CONSTRAINT ck_triagem_tipo
        CHECK (tipo_triagem IN ('ADULTO', 'PEDIATRICA')),
    CONSTRAINT ck_triagem_faixa_etaria
        CHECK (
            (tipo_triagem = 'PEDIATRICA' AND faixaEtariaMeses IS NOT NULL AND faixaEtariaMeses >= 0)
            OR (tipo_triagem = 'ADULTO' AND faixaEtariaMeses IS NULL)
        ),
    CONSTRAINT ck_triagem_fc   CHECK (frequenciaCardiaca     IS NULL OR frequenciaCardiaca     > 0),
    CONSTRAINT ck_triagem_fr   CHECK (frequenciaRespiratoria IS NULL OR frequenciaRespiratoria > 0),
    CONSTRAINT ck_triagem_spo2 CHECK (saturacaoOxigenio      IS NULL OR saturacaoOxigenio BETWEEN 0 AND 100),
    CONSTRAINT ck_triagem_temp CHECK (temperatura            IS NULL OR temperatura BETWEEN 25 AND 45)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- Tabela: atendimento (entidade associativa: paciente x fila x triagem x profissional)
-- -----------------------------------------------------------------------------
CREATE TABLE atendimento (
    id                   INT         NOT NULL AUTO_INCREMENT,
    dataHoraChegada      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status               VARCHAR(30) NOT NULL DEFAULT 'AGUARDANDO',
    paciente_id          INT         NOT NULL,
    profissional_id      INT         NULL,
    triagem_id           INT         NULL,
    fila_codigo_unidade  VARCHAR(50) NOT NULL,
    CONSTRAINT pk_atendimento PRIMARY KEY (id),
    -- Uma triagem pertence a no máximo um atendimento (1:1)
    CONSTRAINT uk_atendimento_triagem UNIQUE (triagem_id),
    CONSTRAINT ck_atendimento_status
        CHECK (status IN ('AGUARDANDO', 'EM_ATENDIMENTO', 'FINALIZADO', 'CANCELADO')),
    CONSTRAINT fk_atendimento_paciente FOREIGN KEY (paciente_id)
        REFERENCES paciente (id_pessoa)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_atendimento_profissional FOREIGN KEY (profissional_id)
        REFERENCES profissional_saude (id_pessoa)
        ON DELETE SET NULL
        ON UPDATE CASCADE,
    CONSTRAINT fk_atendimento_triagem FOREIGN KEY (triagem_id)
        REFERENCES triagem (id)
        ON DELETE SET NULL
        ON UPDATE CASCADE,
    CONSTRAINT fk_atendimento_fila FOREIGN KEY (fila_codigo_unidade)
        REFERENCES fila_atendimento (codigoUnidade)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- Índices para buscas frequentes
-- -----------------------------------------------------------------------------
-- CPF: já coberto por uk_pessoa_cpf (índice UNIQUE).

-- Busca por status (ex.: todos os atendimentos 'AGUARDANDO')
CREATE INDEX idx_atendimento_status
    ON atendimento (status);

-- Consulta principal da fila: atendimentos de uma unidade por status,
-- ordenados por ordem de chegada. Também serve de índice para a FK da fila.
CREATE INDEX idx_atendimento_fila_status_chegada
    ON atendimento (fila_codigo_unidade, status, dataHoraChegada);

-- Histórico de atendimentos de um paciente / profissional
CREATE INDEX idx_atendimento_paciente     ON atendimento (paciente_id);
CREATE INDEX idx_atendimento_profissional ON atendimento (profissional_id);

-- =============================================================================
-- DADOS DE TESTE
-- =============================================================================
START TRANSACTION;

-- Unidades de saúde / filas
INSERT INTO fila_atendimento (codigoUnidade) VALUES
    ('UBS-CENTRO-001'),
    ('UPA-ZONA-NORTE-002');

-- Pessoas (ids explícitos para garantir consistência das FKs)
INSERT INTO pessoa (id, nome, cpf, telefone) VALUES
    (1, 'Dra. Mariana Costa Albuquerque', '123.456.789-09', '(11) 98765-4321'),
    (2, 'Enf. Ricardo Alves Pereira',     '987.654.321-00', '(11) 97654-3210'),
    (3, 'João da Silva Santos',           '111.444.777-35', '(11) 91234-5678'),
    (4, 'Maria Oliveira Souza',           '222.555.888-46', '(11) 92345-6789'),
    (5, 'Lucas Ferreira Lima',            '333.666.999-57', '(11) 93456-7890');

-- Profissionais de saúde
INSERT INTO profissional_saude (id_pessoa, registroConselho, cargo) VALUES
    (1, 'CRM-SP 123456',  'Médico'),
    (2, 'COREN-SP 654321', 'Enfermeiro');

-- Pacientes (paciente 5 é criança - triagem pediátrica)
INSERT INTO paciente (id_pessoa, cartaoSus, dataNascimento) VALUES
    (3, '700000000000001', '1985-03-14'),
    (4, '700000000000002', '1992-07-22'),
    (5, '700000000000003', '2024-05-10');

-- Triagens
INSERT INTO triagem
    (id, tipo_triagem, pressaoArterial, frequenciaCardiaca, temperatura,
     frequenciaRespiratoria, saturacaoOxigenio, faixaEtariaMeses)
VALUES
    (1, 'ADULTO',     '140/90', 98,  37.80, 20, 95.50, NULL),
    (2, 'PEDIATRICA', '90/60',  130, 38.70, 32, 96.00, 18);

-- Atendimentos aguardando na fila
INSERT INTO atendimento
    (id, dataHoraChegada, status, paciente_id, profissional_id, triagem_id, fila_codigo_unidade)
VALUES
    (1, '2026-11-09 08:15:00', 'AGUARDANDO', 3, NULL, 1,    'UBS-CENTRO-001'),
    (2, '2026-11-09 08:32:00', 'AGUARDANDO', 5, NULL, 2,    'UBS-CENTRO-001'),
    (3, '2026-11-09 09:05:00', 'AGUARDANDO', 4, NULL, NULL, 'UPA-ZONA-NORTE-002'); -- ainda sem triagem

COMMIT;

-- =============================================================================
-- Consulta de verificação: fila por unidade em ordem de chegada
-- =============================================================================
-- SELECT a.id, a.fila_codigo_unidade, p.nome, p.cpf, t.tipo_triagem, a.status, a.dataHoraChegada
--   FROM atendimento a
--   JOIN pessoa p       ON p.id = a.paciente_id
--   LEFT JOIN triagem t ON t.id = a.triagem_id
--  WHERE a.status = 'AGUARDANDO'
--  ORDER BY a.fila_codigo_unidade, a.dataHoraChegada;
