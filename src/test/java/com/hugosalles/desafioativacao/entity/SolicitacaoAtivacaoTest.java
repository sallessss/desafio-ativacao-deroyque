package com.hugosalles.desafioativacao.entity;

import com.hugosalles.desafioativacao.entity.SolicitacaoAtivacao;
import com.hugosalles.desafioativacao.entity.StatusSolicitacao;
import com.hugosalles.desafioativacao.exception.TransicaoStatusInvalidaException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SolicitacaoAtivacaoTest {

    private static final Instant INSTANTE = Instant.parse("2026-10-28T12:00:00Z");

    @Test
    void deveCriarPendenteComDatasEIdentificador() {
        var solicitacao = novaSolicitacao();

        assertThat(solicitacao.getId()).isNotNull();
        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.PENDING);
        assertThat(solicitacao.getCriadoEm()).isEqualTo(INSTANTE);
        assertThat(solicitacao.getAtualizadoEm()).isEqualTo(INSTANTE);
        assertThat(solicitacao.getProximaTentativaEm()).isEqualTo(INSTANTE);
        assertThat(solicitacao.getTentativas()).isZero();
    }

    @Test
    void devePercorrerFluxoDeSucesso() {
        var solicitacao = novaSolicitacao();

        solicitacao.iniciarProcessamento(INSTANTE.plusSeconds(1));
        solicitacao.concluir(INSTANTE.plusSeconds(2));

        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.COMPLETED);
        assertThat(solicitacao.getTentativas()).isOne();
        assertThat(solicitacao.getProcessamentoIniciadoEm()).isNull();
    }

    @Test
    void deveReagendarFalhaTemporaria() {
        var solicitacao = novaSolicitacao();
        solicitacao.iniciarProcessamento(INSTANTE);

        solicitacao.reagendar(INSTANTE, Duration.ofSeconds(10));

        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.PENDING);
        assertThat(solicitacao.getProximaTentativaEm()).isEqualTo(INSTANTE.plusSeconds(10));
    }

    @Test
    void deveImpedirTransicaoDeConcluidaParaProcessamento() {
        var solicitacao = novaSolicitacao();
        solicitacao.iniciarProcessamento(INSTANTE);
        solicitacao.concluir(INSTANTE);

        assertThatThrownBy(() -> solicitacao.iniciarProcessamento(INSTANTE))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void deveRejeitarIdentificadoresInvalidosNaCriacao() {
        assertThatThrownBy(() -> SolicitacaoAtivacao.criar("", "CUST-1", "PROD-1", "cliente-a", INSTANTE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identificadorExterno");
        assertThatThrownBy(() -> SolicitacaoAtivacao.criar("EXT-1", "cliente inválido", "PROD-1", "cliente-a", INSTANTE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identificadorCliente");
        assertThatThrownBy(() -> SolicitacaoAtivacao.criar("EXT-1", "CUST-1", "PROD-1", " ", INSTANTE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identificadorConsumidor");
    }

    @Test
    void deveRejeitarResultadosSemProcessamentoIniciado() {
        var solicitacao = novaSolicitacao();

        assertThatThrownBy(() -> solicitacao.concluir(INSTANTE))
                .isInstanceOf(TransicaoStatusInvalidaException.class);
        assertThatThrownBy(() -> solicitacao.falharPermanentemente(INSTANTE))
                .isInstanceOf(TransicaoStatusInvalidaException.class);
        assertThatThrownBy(() -> solicitacao.reagendar(INSTANTE, Duration.ZERO))
                .isInstanceOf(TransicaoStatusInvalidaException.class);
    }

    @Test
    void deveRejeitarTransicoesDepoisDeFalhaPermanente() {
        var solicitacao = novaSolicitacao();
        solicitacao.iniciarProcessamento(INSTANTE);
        solicitacao.falharPermanentemente(INSTANTE);

        assertThatThrownBy(() -> solicitacao.iniciarProcessamento(INSTANTE))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("FAILED");
        assertThatThrownBy(() -> solicitacao.concluir(INSTANTE))
                .isInstanceOf(TransicaoStatusInvalidaException.class)
                .hasMessageContaining("FAILED");
    }

    @Test
    void deveRejeitarAtrasoNegativo() {
        var solicitacao = novaSolicitacao();
        solicitacao.iniciarProcessamento(INSTANTE);

        assertThatThrownBy(() -> solicitacao.reagendar(INSTANTE, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativo");
    }

    private SolicitacaoAtivacao novaSolicitacao() {
        return SolicitacaoAtivacao.criar("EXT-12345", "CUST-10001", "INTERNET-500", "cliente-a", INSTANTE);
    }
}
