package com.hugosalles.desafioativacao.service;

import com.hugosalles.desafioativacao.dto.CriarSolicitacaoRequest;
import com.hugosalles.desafioativacao.exception.SolicitacaoDuplicadaException;
import com.hugosalles.desafioativacao.repository.SolicitacaoAtivacaoRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SolicitacaoServiceTest {

    private final SolicitacaoAtivacaoRepository repository = mock(SolicitacaoAtivacaoRepository.class);
    private final Tracer tracer = mock(Tracer.class);
    private final SolicitacaoService service = new SolicitacaoService(repository,
            Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC), tracer);
    private final CriarSolicitacaoRequest request = new CriarSolicitacaoRequest("EXT-1", "CUST-1", "PROD-1");

    @Test
    void deveConverterSomenteConstraintDeExternalIdEmDuplicidade() {
        var violacao = new ConstraintViolationException(
                "duplicada", new SQLException(), "uk_solicitacoes_external_id");
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("integridade", violacao));

        assertThatThrownBy(() -> service.criarSolicitacao(request, "consumidor"))
                .isInstanceOf(SolicitacaoDuplicadaException.class);
    }

    @Test
    void devePropagarOutrasViolacoesDeIntegridade() {
        var violacao = new DataIntegrityViolationException("outra constraint");
        when(repository.saveAndFlush(any())).thenThrow(violacao);

        assertThatThrownBy(() -> service.criarSolicitacao(request, "consumidor"))
                .isSameAs(violacao);
    }

    @Test
    void devePersistirTraceIdComoCorrelacaoDoProcessamento() {
        var span = mock(Span.class);
        var contexto = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(contexto);
        when(contexto.traceId()).thenReturn("4bf92f3577b34da6a3ce929d0e0e4736");
        when(repository.saveAndFlush(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        var solicitacao = service.criarSolicitacao(request, "consumidor");

        assertThat(solicitacao.getIdentificadorCorrelacao())
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }
}
