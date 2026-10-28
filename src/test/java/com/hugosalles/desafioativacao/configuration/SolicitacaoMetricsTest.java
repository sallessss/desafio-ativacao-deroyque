package com.hugosalles.desafioativacao.configuration;

import com.hugosalles.desafioativacao.entity.StatusSolicitacao;
import com.hugosalles.desafioativacao.repository.SolicitacaoAtivacaoRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SolicitacaoMetricsTest {

    @Test
    void deveAtualizarTodosOsStatusComUmaUnicaConsultaAgrupada() {
        var repository = mock(SolicitacaoAtivacaoRepository.class);
        var contagem = mock(SolicitacaoAtivacaoRepository.ContagemPorStatus.class);
        var meterRegistry = new SimpleMeterRegistry();
        when(contagem.getStatus()).thenReturn(StatusSolicitacao.PENDING);
        when(contagem.getQuantidade()).thenReturn(3L);
        when(repository.contarAgrupadoPorStatus()).thenReturn(List.of(contagem));
        var metrics = new SolicitacaoMetrics(meterRegistry, repository);
        metrics.register();

        metrics.atualizar();

        assertThat(valor(meterRegistry, StatusSolicitacao.PENDING)).isEqualTo(3);
        assertThat(valor(meterRegistry, StatusSolicitacao.COMPLETED)).isZero();
        verify(repository).contarAgrupadoPorStatus();
    }

    private double valor(SimpleMeterRegistry meterRegistry, StatusSolicitacao status) {
        return meterRegistry.get("activation.requests")
                .tag("status", status.name().toLowerCase())
                .gauge()
                .value();
    }
}
