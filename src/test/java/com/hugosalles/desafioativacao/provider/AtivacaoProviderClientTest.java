package com.hugosalles.desafioativacao.provider;

import com.hugosalles.desafioativacao.configuration.ProviderProperties;
import com.hugosalles.desafioativacao.processing.ComandoAtivacao;
import com.hugosalles.desafioativacao.processing.ResultadoAtivacao;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;


import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

class AtivacaoProviderClientTest {

    private static final Instant AGORA = Instant.parse("2026-10-28T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AGORA, ZoneOffset.UTC);

    @Test
    void deveMapearSucessoEEnviarIdempotencia() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder, "http://provedor");
        server.expect(once(), requestTo("http://provedor/provider/v1/activate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "EXT-1"))
                .andExpect(header("X-Correlation-ID", "correlacao-1"))
                .andRespond(withSuccess("{\"status\":\"ACTIVATED\"}", MediaType.APPLICATION_JSON));

        assertThat(client.ativar(comando())).isEqualTo(ResultadoAtivacao.SUCESSO);
        server.verify();
    }

    @Test
    void deveMapearErroDoServidorComoTemporario() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder, "http://provedor");
        server.expect(once(), requestTo("http://provedor/provider/v1/activate")).andRespond(withServerError());

        assertThat(client.ativar(comando())).isEqualTo(ResultadoAtivacao.FALHA_TEMPORARIA);
    }

    @Test
    void deveMapearErroDoClienteComoPermanente() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder, "http://provedor");
        server.expect(once(), requestTo("http://provedor/provider/v1/activate")).andRespond(withBadRequest());

        assertThat(client.ativar(comando())).isEqualTo(ResultadoAtivacao.FALHA_PERMANENTE);
    }

    @Test
    void deveMapearTimeoutComoFalhaTemporaria() {
        var server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        try {
            server.stubFor(post(urlEqualTo("/provider/v1/activate"))
                    .willReturn(aResponse().withFixedDelay(300).withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"status\":\"ACTIVATED\"}")));
            var requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofMillis(100));
            requestFactory.setReadTimeout(Duration.ofMillis(100));
            var client = client(RestClient.builder().requestFactory(requestFactory), server.baseUrl());

            assertThat(client.ativar(comando())).isEqualTo(ResultadoAtivacao.FALHA_TEMPORARIA);
        } finally {
            server.stop();
        }
    }

    @Test
    void deveRespeitarRetryAfterEmSegundosParaLimiteDeTaxa() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder, "http://provedor");
        server.expect(once(), requestTo("http://provedor/provider/v1/activate"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "30"));

        assertThat(client.ativar(comando()))
                .isEqualTo(ResultadoAtivacao.falhaTemporaria(Duration.ofSeconds(30)));
    }

    @Test
    void deveRespeitarRetryAfterEmDataHttpParaIndisponibilidade() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder, "http://provedor");
        String data = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(AGORA.plusSeconds(45), ZoneOffset.UTC));
        server.expect(once(), requestTo("http://provedor/provider/v1/activate"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", data));

        assertThat(client.ativar(comando()))
                .isEqualTo(ResultadoAtivacao.falhaTemporaria(Duration.ofSeconds(45)));
    }

    private ComandoAtivacao comando() {
        return new ComandoAtivacao("EXT-1", "CUST-1", "PROD-1", "correlacao-1");
    }

    private AtivacaoProviderClient client(RestClient.Builder builder, String baseUrl) {
        return new AtivacaoProviderClient(builder, new SimpleMeterRegistry(), properties(baseUrl), CLOCK);
    }

    private ProviderProperties properties(String baseUrl) {
        return new ProviderProperties(baseUrl, "", Duration.ofMillis(100), Duration.ofMillis(100));
    }
}
