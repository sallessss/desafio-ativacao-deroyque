package com.hugosalles.desafioativacao.entity;

import com.hugosalles.desafioativacao.exception.TransicaoStatusInvalidaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "solicitacoes_ativacao")
public class SolicitacaoAtivacao {

    private static final int TAMANHO_MAXIMO_IDENTIFICADOR = 64;
    private static final int TAMANHO_MAXIMO_CONSUMIDOR = 128;
    private static final int TAMANHO_MAXIMO_CORRELACAO = 64;
    private static final Pattern FORMATO_IDENTIFICADOR = Pattern.compile("^[A-Za-z0-9._-]+$");

    @Id
    private UUID id;

    @Column(name = "external_id", nullable = false, length = 64, unique = true)
    private String identificadorExterno;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String identificadorCliente;

    @Column(name = "product_code", nullable = false, length = 64)
    private String codigoProduto;

    @Column(name = "consumer_id", nullable = false, length = 128)
    private String identificadorConsumidor;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String identificadorCorrelacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StatusSolicitacao status;

    @Column(name = "attempts", nullable = false)
    private int tentativas;

    @Column(name = "next_attempt_at")
    private Instant proximaTentativaEm;

    @Column(name = "processing_started_at")
    private Instant processamentoIniciadoEm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "updated_at", nullable = false)
    private Instant atualizadoEm;

    @Version
    @Column(name = "version", nullable = false)
    private long versao;

    protected SolicitacaoAtivacao() {
    }

    public static SolicitacaoAtivacao criar(String identificadorExterno, String identificadorCliente, String codigoProduto, String identificadorConsumidor, Instant agora) {
        return criar(identificadorExterno, identificadorCliente, codigoProduto, identificadorConsumidor,
                UUID.randomUUID().toString(), agora);
    }

    public static SolicitacaoAtivacao criar(String identificadorExterno, String identificadorCliente,
                                             String codigoProduto, String identificadorConsumidor,
                                             String identificadorCorrelacao, Instant agora) {
        Objects.requireNonNull(agora, "agora é obrigatório");
        SolicitacaoAtivacao solicitacao = new SolicitacaoAtivacao();
        solicitacao.id = UUID.randomUUID();
        solicitacao.identificadorExterno = validarIdentificador(identificadorExterno, "identificadorExterno");
        solicitacao.identificadorCliente = validarIdentificador(identificadorCliente, "identificadorCliente");
        solicitacao.codigoProduto = validarIdentificador(codigoProduto, "codigoProduto");
        solicitacao.identificadorConsumidor = validarConsumidor(identificadorConsumidor);
        solicitacao.identificadorCorrelacao = validarCorrelacao(identificadorCorrelacao);
        solicitacao.status = StatusSolicitacao.PENDING;
        solicitacao.proximaTentativaEm = agora;
        solicitacao.criadoEm = agora;
        solicitacao.atualizadoEm = agora;
        return solicitacao;
    }

    public void iniciarProcessamento(Instant agora) {
        Objects.requireNonNull(agora, "agora é obrigatório");
        exigirStatus(StatusSolicitacao.PENDING, StatusSolicitacao.PROCESSING);
        status = StatusSolicitacao.PROCESSING;
        tentativas++;
        processamentoIniciadoEm = agora;
        proximaTentativaEm = null;
        atualizadoEm = agora;
    }

    public void concluir(Instant agora) {
        Objects.requireNonNull(agora, "agora é obrigatório");
        exigirStatus(StatusSolicitacao.PROCESSING, StatusSolicitacao.COMPLETED);
        status = StatusSolicitacao.COMPLETED;
        processamentoIniciadoEm = null;
        atualizadoEm = agora;
    }

    public void falharPermanentemente(Instant agora) {
        Objects.requireNonNull(agora, "agora é obrigatório");
        exigirStatus(StatusSolicitacao.PROCESSING, StatusSolicitacao.FAILED);
        status = StatusSolicitacao.FAILED;
        processamentoIniciadoEm = null;
        atualizadoEm = agora;
    }

    public void reagendar(Instant agora, Duration atraso) {
        Objects.requireNonNull(agora, "agora é obrigatório");
        Objects.requireNonNull(atraso, "atraso é obrigatório");
        if (atraso.isNegative()) {
            throw new IllegalArgumentException("atraso não pode ser negativo");
        }
        exigirStatus(StatusSolicitacao.PROCESSING, StatusSolicitacao.PENDING);
        status = StatusSolicitacao.PENDING;
        processamentoIniciadoEm = null;
        proximaTentativaEm = agora.plus(atraso);
        atualizadoEm = agora;
    }

    public void recuperarProcessamentoInterrompido(Instant agora) {
        reagendar(agora, Duration.ZERO);
    }

    private void exigirStatus(StatusSolicitacao atualEsperado, StatusSolicitacao destino) {
        if (status != atualEsperado) {
            throw new TransicaoStatusInvalidaException(
                    "Transição inválida de %s para %s".formatted(status, destino));
        }
    }

    private static String validarIdentificador(String valor, String campo) {
        if (valor == null || valor.isBlank() || valor.length() > TAMANHO_MAXIMO_IDENTIFICADOR
                || !FORMATO_IDENTIFICADOR.matcher(valor).matches()) {
            throw new IllegalArgumentException(campo + " possui formato ou tamanho inválido");
        }
        return valor;
    }

    private static String validarConsumidor(String consumidor) {
        if (consumidor == null || consumidor.isBlank() || consumidor.length() > TAMANHO_MAXIMO_CONSUMIDOR) {
            throw new IllegalArgumentException("identificadorConsumidor possui tamanho inválido");
        }
        return consumidor;
    }

    private static String validarCorrelacao(String identificadorCorrelacao) {
        if (identificadorCorrelacao == null || identificadorCorrelacao.isBlank()
                || identificadorCorrelacao.length() > TAMANHO_MAXIMO_CORRELACAO) {
            throw new IllegalArgumentException("identificadorCorrelacao possui tamanho inválido");
        }
        return identificadorCorrelacao;
    }

    public UUID getId() { return id; }
    public String getIdentificadorExterno() { return identificadorExterno; }
    public String getIdentificadorCliente() { return identificadorCliente; }
    public String getCodigoProduto() { return codigoProduto; }
    public String getIdentificadorConsumidor() { return identificadorConsumidor; }
    public String getIdentificadorCorrelacao() { return identificadorCorrelacao; }
    public StatusSolicitacao getStatus() { return status; }
    public int getTentativas() { return tentativas; }
    public Instant getProximaTentativaEm() { return proximaTentativaEm; }
    public Instant getProcessamentoIniciadoEm() { return processamentoIniciadoEm; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public long getVersao() { return versao; }
}
