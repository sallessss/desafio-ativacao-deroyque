package com.hugosalles.desafioativacao.integration;

import com.hugosalles.desafioativacao.dto.CriarSolicitacaoRequest;
import com.hugosalles.desafioativacao.exception.SolicitacaoDuplicadaException;
import com.hugosalles.desafioativacao.exception.SolicitacaoNaoEncontradaException;
import com.hugosalles.desafioativacao.processing.ProcessamentoTransactionService;
import com.hugosalles.desafioativacao.repository.SolicitacaoAtivacaoRepository;
import com.hugosalles.desafioativacao.service.SolicitacaoService;
import com.hugosalles.desafioativacao.entity.StatusSolicitacao;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {"processamento.worker-habilitado=false", "management.tracing.enabled=false"})
class SolicitacaoPersistenceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired SolicitacaoService service;
    @Autowired SolicitacaoAtivacaoRepository repository;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired ProcessamentoTransactionService transactionService;

    @BeforeEach
    void limpar() {
        repository.deleteAll();
    }

    @Test
    void deveArbitrarCriacoesConcorrentesPelaConstraint() throws Exception {
        var inicio = new CountDownLatch(1);
        var requisicao = new CriarSolicitacaoRequest("EXT-CONCORRENTE", "CUST-1", "PROD-1");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(() -> criarApos(inicio, requisicao));
            var segunda = executor.submit(() -> criarApos(inicio, requisicao));
            inicio.countDown();
            var resultados = java.util.List.of(primeira.get(), segunda.get());
            assertThat(resultados).containsExactlyInAnyOrder("criada", "duplicada");
        }
        assertThat(repository.count()).isOne();
    }

    @Test
    void deveFiltrarEPaginarSomenteDadosDoConsumidor() {
        service.criarSolicitacao(new CriarSolicitacaoRequest("EXT-1", "CUST-1", "PROD-1"), "consumidor-a");
        service.criarSolicitacao(new CriarSolicitacaoRequest("EXT-2", "CUST-2", "PROD-2"), "consumidor-a");
        service.criarSolicitacao(new CriarSolicitacaoRequest("EXT-3", "CUST-1", "PROD-3"), "consumidor-b");

        var pagina = service.listarSolicitacoes(StatusSolicitacao.PENDING, "CUST-1", 0, 20, "consumidor-a");

        assertThat(pagina.getTotalElements()).isOne();
        assertThat(pagina.getContent()).extracting("identificadorExterno").containsExactly("EXT-1");
    }

    @Test
    void deveOcultarSolicitacaoDeOutroConsumidor() {
        var criada = service.criarSolicitacao(
                new CriarSolicitacaoRequest("EXT-BOLA", "CUST-1", "PROD-1"), "consumidor-a");

        assertThatThrownBy(() -> service.buscarSolicitacao(criada.getId(), "consumidor-b"))
                .isInstanceOf(SolicitacaoNaoEncontradaException.class);
    }

    @Test
    void deveDistribuirClaimsConcorrentesSemDuplicarTrabalho() throws Exception {
        for (int indice = 1; indice <= 4; indice++) {
            service.criarSolicitacao(new CriarSolicitacaoRequest(
                    "EXT-CLAIM-" + indice, "CUST-1", "PROD-1"), "consumidor");
        }
        var inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeiro = executor.submit(() -> reclamarApos(inicio, 2));
            var segundo = executor.submit(() -> reclamarApos(inicio, 2));
            inicio.countDown();

            var primeiroLote = primeiro.get();
            var segundoLote = segundo.get();
            var todos = Stream.concat(primeiroLote.stream(), segundoLote.stream()).toList();

            assertThat(primeiroLote).hasSize(2);
            assertThat(segundoLote).hasSize(2);
            assertThat(todos).doesNotHaveDuplicates().hasSize(4);
            assertThat(repository.findAllById(todos))
                    .allMatch(solicitacao -> solicitacao.getStatus() == StatusSolicitacao.PROCESSING);
        }
    }

    @Test
    void deveDetectarAtualizacaoConcorrentePelaVersao() {
        var criada = service.criarSolicitacao(
                new CriarSolicitacaoRequest("EXT-VERSAO", "CUST-1", "PROD-1"), "consumidor");
        var primeiraSessao = entityManagerFactory.createEntityManager();
        var segundaSessao = entityManagerFactory.createEntityManager();
        try {
            var primeiraCopia = primeiraSessao.find(com.hugosalles.desafioativacao.entity.SolicitacaoAtivacao.class,
                    criada.getId());
            var segundaCopia = segundaSessao.find(com.hugosalles.desafioativacao.entity.SolicitacaoAtivacao.class,
                    criada.getId());

            primeiraSessao.getTransaction().begin();
            primeiraCopia.iniciarProcessamento(java.time.Instant.now());
            primeiraSessao.getTransaction().commit();

            segundaSessao.getTransaction().begin();
            segundaCopia.iniciarProcessamento(java.time.Instant.now());
            assertThatThrownBy(() -> segundaSessao.getTransaction().commit())
                    .isInstanceOf(RollbackException.class);
        } finally {
            primeiraSessao.close();
            segundaSessao.close();
        }
    }

    private String criarApos(CountDownLatch inicio, CriarSolicitacaoRequest requisicao) throws Exception {
        inicio.await();
        try {
            service.criarSolicitacao(requisicao, "consumidor");
            return "criada";
        } catch (SolicitacaoDuplicadaException excecao) {
            return "duplicada";
        }
    }

    private java.util.List<java.util.UUID> reclamarApos(CountDownLatch inicio, int limite) throws Exception {
        inicio.await();
        return transactionService.reclamar(limite);
    }
}
