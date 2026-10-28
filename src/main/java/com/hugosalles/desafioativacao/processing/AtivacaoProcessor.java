package com.hugosalles.desafioativacao.processing;

import com.hugosalles.desafioativacao.configuration.ProcessingProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "processamento.worker-habilitado", havingValue = "true", matchIfMissing = true)
public class AtivacaoProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AtivacaoProcessor.class);

    private final ProcessamentoTransactionService transactionService;
    private final ProvedorAtivacao provedorAtivacao;
    private final Duration stuckTimeout;
    private final int tamanhoLote;

    public AtivacaoProcessor(ProcessamentoTransactionService transactionService, ProvedorAtivacao provedorAtivacao, ProcessingProperties properties) {
        this.transactionService = transactionService;
        this.provedorAtivacao = provedorAtivacao;
        this.stuckTimeout = properties.timeoutPreso();
        this.tamanhoLote = properties.tamanhoLote();
    }

    @Scheduled(fixedDelayString = "${processamento.intervalo-worker}")
    public void processar() {
        recuperarProcessamentosPresos();
        for (var id : reclamarSolicitacoes()) {
            processar(id);
        }
    }

    private void recuperarProcessamentosPresos() {
        try {
            transactionService.recuperarPresos(stuckTimeout, tamanhoLote);
        } catch (RuntimeException exception) {
            LOG.error("Falha ao recuperar processamentos presos", exception);
        }
    }

    private List<UUID> reclamarSolicitacoes() {
        try {
            return transactionService.reclamar(tamanhoLote);
        } catch (RuntimeException exception) {
            LOG.error("Falha ao reclamar solicitações pendentes", exception);
            return List.of();
        }
    }

    private void processar(UUID id) {
        try {
            var solicitacao = transactionService.carregar(id);
            try (var ignored = MDC.putCloseable("correlationId", solicitacao.getIdentificadorCorrelacao())) {
                var comando = new ComandoAtivacao(solicitacao.getIdentificadorExterno(),
                        solicitacao.getIdentificadorCliente(), solicitacao.getCodigoProduto(),
                        solicitacao.getIdentificadorCorrelacao());
                var resultado = provedorAtivacao.ativar(comando);
                transactionService.registrarResultado(id, resultado);
            }
        } catch (RuntimeException exception) {
            LOG.error("Falha inesperada ao processar solicitação id={}; será reconciliada se permanecer em PROCESSING",
                    id, exception);
        }
    }
}
