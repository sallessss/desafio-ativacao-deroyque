package com.hugosalles.desafioativacao.configuration;

import com.hugosalles.desafioativacao.entity.StatusSolicitacao;
import com.hugosalles.desafioativacao.repository.SolicitacaoAtivacaoRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SolicitacaoMetrics {

    private static final Logger LOG = LoggerFactory.getLogger(SolicitacaoMetrics.class);

    private final MeterRegistry meterRegistry;
    private final SolicitacaoAtivacaoRepository repository;
    private final EnumMap<StatusSolicitacao, AtomicLong> contadores = new EnumMap<>(StatusSolicitacao.class);

    public SolicitacaoMetrics(MeterRegistry meterRegistry, SolicitacaoAtivacaoRepository repository) {
        this.meterRegistry = meterRegistry;
        this.repository = repository;
        for (var status : StatusSolicitacao.values()) {
            contadores.put(status, new AtomicLong());
        }
    }

    @PostConstruct
    void register() {
        for (var status : StatusSolicitacao.values()) {
            Gauge.builder("activation.requests", contadores.get(status), AtomicLong::get)
                    .description("Quantidade persistida de solicitações por status")
                    .tag("status", status.name().toLowerCase())
                    .register(meterRegistry);
        }
    }

    @Scheduled(fixedDelayString = "${observabilidade.metricas-status-intervalo:15s}")
    void atualizar() {
        try {
            var novosValores = new EnumMap<StatusSolicitacao, Long>(StatusSolicitacao.class);
            for (var status : StatusSolicitacao.values()) {
                novosValores.put(status, 0L);
            }
            for (var contagem : repository.contarAgrupadoPorStatus()) {
                novosValores.put(contagem.getStatus(), contagem.getQuantidade());
            }
            novosValores.forEach((status, quantidade) -> contadores.get(status).set(quantidade));
        } catch (RuntimeException exception) {
            LOG.warn("Não foi possível atualizar a métrica agregada de solicitações por status", exception);
        }
    }
}
