package com.hugosalles.desafioativacao.processing;

import com.hugosalles.desafioativacao.configuration.ProcessingProperties;
import com.hugosalles.desafioativacao.entity.SolicitacaoAtivacao;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;

class AtivacaoProcessorTest {

    @Test
    void deveIsolarFalhaInesperadaEContinuarProcessandoLote() {
        var transactionService = mock(ProcessamentoTransactionService.class);
        var provedorAtivacao = mock(ProvedorAtivacao.class);
        var primeira = SolicitacaoAtivacao.criar("EXT-1", "CUST-1", "PROD-1", "consumidor", Instant.EPOCH);
        var segunda = SolicitacaoAtivacao.criar("EXT-2", "CUST-2", "PROD-2", "consumidor", Instant.EPOCH);
        when(transactionService.reclamar(20)).thenReturn(List.of(primeira.getId(), segunda.getId()));
        when(transactionService.carregar(primeira.getId())).thenReturn(primeira);
        when(transactionService.carregar(segunda.getId())).thenReturn(segunda);
        when(provedorAtivacao.ativar(new ComandoAtivacao("EXT-1", "CUST-1", "PROD-1",
                primeira.getIdentificadorCorrelacao())))
                .thenThrow(new IllegalStateException("falha inesperada"));
        when(provedorAtivacao.ativar(new ComandoAtivacao("EXT-2", "CUST-2", "PROD-2",
                segunda.getIdentificadorCorrelacao())))
                .thenReturn(ResultadoAtivacao.SUCESSO);

        var properties = new ProcessingProperties(
                4, Duration.ofSeconds(5), Duration.ofSeconds(2), Duration.ofMinutes(2), 20, true);
        new AtivacaoProcessor(transactionService, provedorAtivacao, properties).processar();

        verify(transactionService).recuperarPresos(Duration.ofMinutes(2), 20);
        verify(transactionService, never()).registrarResultado(eq(primeira.getId()), any());
        verify(transactionService).registrarResultado(segunda.getId(), ResultadoAtivacao.SUCESSO);
    }
}
