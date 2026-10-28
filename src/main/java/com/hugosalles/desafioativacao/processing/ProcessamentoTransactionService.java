package com.hugosalles.desafioativacao.processing;

import com.hugosalles.desafioativacao.entity.SolicitacaoAtivacao;
import com.hugosalles.desafioativacao.repository.SolicitacaoAtivacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProcessamentoTransactionService {

    private final SolicitacaoAtivacaoRepository repository;
    private final Clock clock;
    private final PoliticaRetentativa retryPolicy;

    public ProcessamentoTransactionService(SolicitacaoAtivacaoRepository repository, Clock clock, PoliticaRetentativa retryPolicy) {
        this.repository = repository;
        this.clock = clock;
        this.retryPolicy = retryPolicy;
    }

    @Transactional
    public List<UUID> reclamar(int limite) {
        Instant agora = Instant.now(clock);
        var solicitacoes = repository.buscarPendentesParaReserva(agora, limite);
        solicitacoes.forEach(solicitacao -> solicitacao.iniciarProcessamento(agora));
        repository.flush();
        return solicitacoes.stream().map(SolicitacaoAtivacao::getId).toList();
    }

    @Transactional(readOnly = true)
    public SolicitacaoAtivacao carregar(UUID id) {
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public void registrarResultado(UUID id, ResultadoAtivacao resultado) {
        var solicitacao = repository.findById(id).orElseThrow();
        Instant agora = Instant.now(clock);
        switch (resultado.tipo()) {
            case SUCESSO -> solicitacao.concluir(agora);
            case FALHA_PERMANENTE -> solicitacao.falharPermanentemente(agora);
            case FALHA_TEMPORARIA -> {
                if (retryPolicy.esgotou(solicitacao.getTentativas())) solicitacao.falharPermanentemente(agora);
                else solicitacao.reagendar(agora, resultado.atrasoSugerido() != null
                        ? resultado.atrasoSugerido()
                        : retryPolicy.calcularAtraso(
                                solicitacao.getTentativas(), solicitacao.getIdentificadorExterno()));
            }
        }
    }

    @Transactional
    public int recuperarPresos(Duration timeout, int limite) {
        Instant agora = Instant.now(clock);
        var presos = repository.buscarProcessamentosPresos(agora.minus(timeout), limite);
        presos.forEach(solicitacao -> {
            if (retryPolicy.esgotou(solicitacao.getTentativas())) solicitacao.falharPermanentemente(agora);
            else solicitacao.recuperarProcessamentoInterrompido(agora);
        });
        return presos.size();
    }
}
