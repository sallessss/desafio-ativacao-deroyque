package com.hugosalles.desafioativacao.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.hugosalles.desafioativacao.dto.CriarSolicitacaoRequest;
import com.hugosalles.desafioativacao.entity.StatusSolicitacao;
import com.hugosalles.desafioativacao.repository.SolicitacaoAtivacaoRepository;
import com.hugosalles.desafioativacao.service.SolicitacaoService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(properties = {
        "processamento.intervalo-worker=50ms",
        "processamento.maximo-tentativas=2",
        "processamento.atraso-base=10ms",
        "processamento.timeout-preso=1s",
        "provedor.timeout-conexao=100ms",
        "provedor.timeout-leitura=100ms",
        "management.tracing.enabled=false"
})
class ProcessamentoAtivacaoIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static final WireMockServer PROVIDER = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        PROVIDER.start();
    }

    @DynamicPropertySource
    static void configureDependencies(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("provedor.url-base", PROVIDER::baseUrl);
    }

    @Autowired
    SolicitacaoService service;

    @Autowired
    SolicitacaoAtivacaoRepository repository;

    static ScheduledAnnotationBeanPostProcessor SCHEDULED_TASKS;

    @Autowired
    void setScheduledTasks(ScheduledAnnotationBeanPostProcessor scheduledTasks) {
        SCHEDULED_TASKS = scheduledTasks;
    }

    @BeforeEach
    void preparar() {
        repository.deleteAll();
        PROVIDER.resetAll();
    }

    @AfterAll
    static void encerrarRecursos() {
        SCHEDULED_TASKS.destroy();
        PROVIDER.stop();
    }

    @Test
    void deveConcluirAtivacaoAssincronaComIdempotencia() {
        PROVIDER.stubFor(post(urlEqualTo("/provider/v1/activate"))
                .willReturn(okJson("{\"status\":\"ACTIVATED\"}")));

        UUID id = criar("EXT-SUCESSO");

        aguardarStatus(id, StatusSolicitacao.COMPLETED);
        PROVIDER.verify(1, postRequestedFor(urlEqualTo("/provider/v1/activate"))
                .withHeader("Idempotency-Key", equalTo("EXT-SUCESSO"))
                .withHeader("X-Correlation-ID", matching(".+")));
    }

    @Test
    void deveFalharSemRetentativaParaErroPermanente() {
        PROVIDER.stubFor(post(urlEqualTo("/provider/v1/activate"))
                .willReturn(badRequest()));

        UUID id = criar("EXT-PERMANENTE");

        aguardarStatus(id, StatusSolicitacao.FAILED);
        PROVIDER.verify(1, postRequestedFor(urlEqualTo("/provider/v1/activate")));
    }

    @Test
    void deveRetentarFalhaTemporariaEConcluir() {
        PROVIDER.stubFor(post(urlEqualTo("/provider/v1/activate"))
                .inScenario("retentativa")
                .whenScenarioStateIs(STARTED)
                .willReturn(serverError())
                .willSetStateTo("segunda-tentativa"));
        PROVIDER.stubFor(post(urlEqualTo("/provider/v1/activate"))
                .inScenario("retentativa")
                .whenScenarioStateIs("segunda-tentativa")
                .willReturn(okJson("{\"status\":\"ACTIVATED\"}")));

        UUID id = criar("EXT-TEMPORARIA");

        aguardarStatus(id, StatusSolicitacao.COMPLETED);
        assertThat(repository.findById(id).orElseThrow().getTentativas()).isEqualTo(2);
        PROVIDER.verify(2, postRequestedFor(urlEqualTo("/provider/v1/activate")));
    }

    @Test
    void deveEsgotarRetentativasAposTimeoutAmbiguo() {
        PROVIDER.stubFor(post(urlEqualTo("/provider/v1/activate"))
                .willReturn(okJson("{\"status\":\"ACTIVATED\"}").withFixedDelay(300)));

        UUID id = criar("EXT-TIMEOUT");

        aguardarStatus(id, StatusSolicitacao.FAILED);
        assertThat(repository.findById(id).orElseThrow().getTentativas()).isEqualTo(2);
        PROVIDER.verify(2, postRequestedFor(urlEqualTo("/provider/v1/activate")));
    }

    @Test
    void deveRespeitarRetryAfterEConcluirNaTentativaSeguinte() {
        PROVIDER.stubFor(post(urlEqualTo("/provider/v1/activate"))
                .inScenario("retry-after")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0"))
                .willSetStateTo("segunda-tentativa"));
        PROVIDER.stubFor(post(urlEqualTo("/provider/v1/activate"))
                .inScenario("retry-after")
                .whenScenarioStateIs("segunda-tentativa")
                .willReturn(okJson("{\"status\":\"ACTIVATED\"}")));

        UUID id = criar("EXT-RETRY-AFTER");

        aguardarStatus(id, StatusSolicitacao.COMPLETED);
        assertThat(repository.findById(id).orElseThrow().getTentativas()).isEqualTo(2);
        PROVIDER.verify(2, postRequestedFor(urlEqualTo("/provider/v1/activate"))
                .withHeader("X-Correlation-ID", equalTo(
                        repository.findById(id).orElseThrow().getIdentificadorCorrelacao())));
    }

    private UUID criar(String identificadorExterno) {
        return service.criarSolicitacao(new CriarSolicitacaoRequest(
                identificadorExterno, "CUST-1", "PROD-1"), "consumidor").getId();
    }

    private void aguardarStatus(UUID id, StatusSolicitacao esperado) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(esperado));
    }
}
