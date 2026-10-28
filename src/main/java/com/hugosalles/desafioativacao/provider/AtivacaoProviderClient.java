package com.hugosalles.desafioativacao.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hugosalles.desafioativacao.processing.ComandoAtivacao;
import com.hugosalles.desafioativacao.processing.ProvedorAtivacao;
import com.hugosalles.desafioativacao.processing.ResultadoAtivacao;
import com.hugosalles.desafioativacao.configuration.ProviderProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class AtivacaoProviderClient implements ProvedorAtivacao {

    private final RestClient client;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public AtivacaoProviderClient(RestClient.Builder builder,
                                  MeterRegistry meterRegistry,
                                  ProviderProperties properties,
                                  Clock clock) {
        this.client = builder.baseUrl(properties.urlBase())
                .defaultHeaders(headers -> {
                    if (!properties.token().isBlank()) headers.setBearerAuth(properties.token());
                }).build();
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Override
    public ResultadoAtivacao ativar(ComandoAtivacao comando) {
        Timer.Sample sample = Timer.start(meterRegistry);
        ResultadoAtivacao resultado;
        try {
            var response = client.post().uri("/provider/v1/activate")
                    .header("Idempotency-Key", comando.identificadorExterno())
                    .header("X-Correlation-ID", comando.identificadorCorrelacao())
                    .body(new ProviderRequest(comando.identificadorExterno(),
                            comando.identificadorCliente(), comando.codigoProduto()))
                    .retrieve().body(ProviderResponse.class);
            resultado = response != null && "ACTIVATED".equals(response.status())
                    ? ResultadoAtivacao.SUCESSO : ResultadoAtivacao.FALHA_PERMANENTE;
        } catch (RestClientResponseException exception) {
            resultado = temporario(exception.getStatusCode())
                    ? ResultadoAtivacao.falhaTemporaria(
                            atrasoSugerido(exception.getStatusCode(), exception.getResponseHeaders()))
                    : ResultadoAtivacao.FALHA_PERMANENTE;
        } catch (ResourceAccessException exception) {
            resultado = ResultadoAtivacao.FALHA_TEMPORARIA;
        } catch (RestClientException exception) {
            resultado = containsIOException(exception)
                    ? ResultadoAtivacao.FALHA_TEMPORARIA : ResultadoAtivacao.FALHA_PERMANENTE;
        }
        sample.stop(meterRegistry.timer("provider.activation.duration", "result", resultado.name().toLowerCase()));
        meterRegistry.counter("provider.activation.requests", "result", resultado.name().toLowerCase()).increment();
        return resultado;
    }

    private boolean temporario(HttpStatusCode status) {
        return status.is5xxServerError() || status.value() == 408 || status.value() == 429;
    }

    private Duration atrasoSugerido(HttpStatusCode status, org.springframework.http.HttpHeaders headers) {
        if ((status.value() != 429 && status.value() != 503) || headers == null) return null;
        String valor = headers.getFirst("Retry-After");
        if (valor == null || valor.isBlank()) return null;
        try {
            long segundos = Long.parseLong(valor.trim());
            return segundos < 0 ? null : Duration.ofSeconds(segundos);
        } catch (NumberFormatException ignored) {
            try {
                Instant instante = ZonedDateTime.parse(valor.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Duration atraso = Duration.between(Instant.now(clock), instante);
                return atraso.isNegative() ? Duration.ZERO : atraso;
            } catch (DateTimeParseException invalidDate) {
                return null;
            }
        }
    }

    private boolean containsIOException(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof IOException) return true;
            cause = cause.getCause();
        }
        return false;
    }

    record ProviderRequest(@JsonProperty("externalId") String identificadorExterno,
                              @JsonProperty("customerId") String identificadorCliente,
                              @JsonProperty("productCode") String codigoProduto) {}

    record ProviderResponse(@JsonProperty("status") String status) {}
}
