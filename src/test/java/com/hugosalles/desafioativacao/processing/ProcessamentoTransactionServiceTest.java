package com.hugosalles.desafioativacao.processing;

import com.hugosalles.desafioativacao.configuration.ProcessingProperties;
import com.hugosalles.desafioativacao.entity.SolicitacaoAtivacao;
import com.hugosalles.desafioativacao.entity.StatusSolicitacao;
import com.hugosalles.desafioativacao.repository.SolicitacaoAtivacaoRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProcessamentoTransactionServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-28T12:00:00Z");
    private final SolicitacaoAtivacaoRepository repository = mock(SolicitacaoAtivacaoRepository.class);
    private final PoliticaRetentativa retryPolicy = new PoliticaRetentativa(new ProcessingProperties(
            2, Duration.ofSeconds(5), Duration.ofSeconds(2), Duration.ofMinutes(2), 20, true));
    private final ProcessamentoTransactionService transactionService = new ProcessamentoTransactionService(
            repository, Clock.fixed(AGORA, ZoneOffset.UTC), retryPolicy);

    @Test
    void deveReclamarPendenteAntesDaChamadaRemota() {
        var solicitacao = solicitacao();
        when(repository.buscarPendentesParaReserva(AGORA, 20)).thenReturn(List.of(solicitacao));

        assertThat(transactionService.reclamar(20)).containsExactly(solicitacao.getId());
        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.PROCESSING);
        assertThat(solicitacao.getTentativas()).isOne();
        verify(repository).flush();
    }

    @Test
    void deveReagendarFalhaTemporariaAntesDoLimite() {
        var solicitacao = solicitacao();
        solicitacao.iniciarProcessamento(AGORA);
        when(repository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));

        transactionService.registrarResultado(solicitacao.getId(), ResultadoAtivacao.FALHA_TEMPORARIA);

        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.PENDING);
        assertThat(solicitacao.getProximaTentativaEm()).isAfter(AGORA);
    }

    @Test
    void deveFalharQuandoTentativasEsgotarem() {
        var solicitacao = solicitacao();
        solicitacao.iniciarProcessamento(AGORA);
        solicitacao.reagendar(AGORA, Duration.ZERO);
        solicitacao.iniciarProcessamento(AGORA);
        when(repository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));

        transactionService.registrarResultado(solicitacao.getId(), ResultadoAtivacao.FALHA_TEMPORARIA);

        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.FAILED);
    }

    @Test
    void deveRespeitarAtrasoSugeridoPeloProvedor() {
        var solicitacao = solicitacao();
        solicitacao.iniciarProcessamento(AGORA);
        when(repository.findById(solicitacao.getId())).thenReturn(Optional.of(solicitacao));

        transactionService.registrarResultado(solicitacao.getId(),
                ResultadoAtivacao.falhaTemporaria(Duration.ofSeconds(30)));

        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.PENDING);
        assertThat(solicitacao.getProximaTentativaEm()).isEqualTo(AGORA.plusSeconds(30));
    }

    @Test
    void deveRecuperarProcessamentoPresoAntesDoLimite() {
        var solicitacao = solicitacao();
        solicitacao.iniciarProcessamento(AGORA.minusSeconds(180));
        when(repository.buscarProcessamentosPresos(AGORA.minusSeconds(120), 20))
                .thenReturn(List.of(solicitacao));

        assertThat(transactionService.recuperarPresos(Duration.ofMinutes(2), 20)).isOne();
        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.PENDING);
        assertThat(solicitacao.getProximaTentativaEm()).isEqualTo(AGORA);
    }

    @Test
    void deveFinalizarProcessamentoPresoComTentativasEsgotadas() {
        var solicitacao = solicitacao();
        solicitacao.iniciarProcessamento(AGORA.minusSeconds(300));
        solicitacao.reagendar(AGORA.minusSeconds(240), Duration.ZERO);
        solicitacao.iniciarProcessamento(AGORA.minusSeconds(180));
        when(repository.buscarProcessamentosPresos(AGORA.minusSeconds(120), 20))
                .thenReturn(List.of(solicitacao));

        assertThat(transactionService.recuperarPresos(Duration.ofMinutes(2), 20)).isOne();
        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.FAILED);
    }

    private SolicitacaoAtivacao solicitacao() {
        return SolicitacaoAtivacao.criar("EXT-1", "CUST-1", "PROD-1", "consumidor", AGORA);
    }
}
