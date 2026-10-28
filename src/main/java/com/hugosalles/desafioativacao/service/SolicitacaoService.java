package com.hugosalles.desafioativacao.service;

import com.hugosalles.desafioativacao.dto.CriarSolicitacaoRequest;
import com.hugosalles.desafioativacao.entity.SolicitacaoAtivacao;
import com.hugosalles.desafioativacao.entity.StatusSolicitacao;
import com.hugosalles.desafioativacao.exception.SolicitacaoDuplicadaException;
import com.hugosalles.desafioativacao.exception.SolicitacaoNaoEncontradaException;
import com.hugosalles.desafioativacao.repository.SolicitacaoAtivacaoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.hibernate.exception.ConstraintViolationException;
import io.micrometer.tracing.Tracer;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class SolicitacaoService {

    private final SolicitacaoAtivacaoRepository repository;
    private final Clock clock;
    private final Tracer tracer;

    public SolicitacaoService(SolicitacaoAtivacaoRepository repository, Clock clock, Tracer tracer) {
        this.repository = repository;
        this.clock = clock;
        this.tracer = tracer;
    }

    @Transactional
    public SolicitacaoAtivacao criarSolicitacao(CriarSolicitacaoRequest request, String consumidor) {
        validarConsumidor(consumidor);
        var solicitacao = SolicitacaoAtivacao.criar(request.identificadorExterno(),
                request.identificadorCliente(), request.codigoProduto(), consumidor,
                identificadorCorrelacaoAtual(), Instant.now(clock));
        try {
            return repository.saveAndFlush(solicitacao);
        } catch (DataIntegrityViolationException excecao) {
            if (violouUnicidadeDoIdentificadorExterno(excecao)) {
                throw new SolicitacaoDuplicadaException();
            }
            throw excecao;
        }
    }

    @Transactional(readOnly = true)
    public SolicitacaoAtivacao buscarSolicitacao(UUID id, String consumidor) {
        validarConsumidor(consumidor);
        return repository.buscarPorIdEConsumidor(id, consumidor)
                .orElseThrow(SolicitacaoNaoEncontradaException::new);
    }

    @Transactional(readOnly = true)
    public Page<SolicitacaoAtivacao> listarSolicitacoes(StatusSolicitacao status, String cliente, int page, int size, String consumidor) {
        validarConsumidor(consumidor);
        Specification<SolicitacaoAtivacao> filtro = (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("identificadorConsumidor"), consumidor);
        if (status != null) {
            filtro = filtro.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status));
        }
        if (cliente != null && !cliente.isBlank()) {
            filtro = filtro.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("identificadorCliente"), cliente));
        }
        var sort = Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id"));
        return repository.findAll(filtro, PageRequest.of(page, size, sort));
    }

    private void validarConsumidor(String consumidor) {
        if (consumidor == null || consumidor.isBlank() || consumidor.length() > 128) {
            throw new AccessDeniedException("Identidade do consumidor inválida");
        }
    }

    private String identificadorCorrelacaoAtual() {
        var span = tracer.currentSpan();
        if (span == null || span.context().traceId().isBlank()) return UUID.randomUUID().toString();
        return span.context().traceId();
    }

    private boolean violouUnicidadeDoIdentificadorExterno(Throwable excecao) {
        Throwable causa = excecao;
        while (causa != null) {
            if (causa instanceof ConstraintViolationException violacao
                    && "uk_solicitacoes_external_id".equalsIgnoreCase(violacao.getConstraintName())) {
                return true;
            }
            causa = causa.getCause();
        }
        return false;
    }
}
