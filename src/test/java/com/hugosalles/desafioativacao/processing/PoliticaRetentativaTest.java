package com.hugosalles.desafioativacao.processing;

import com.hugosalles.desafioativacao.configuration.ProcessingProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PoliticaRetentativaTest {

    private final PoliticaRetentativa politica = new PoliticaRetentativa(new ProcessingProperties(
            4, Duration.ofSeconds(5), Duration.ofSeconds(2), Duration.ofMinutes(2), 20, true));

    @Test
    void deveAplicarBackoffExponencialComJitterLimitado() {
        var primeiro = politica.calcularAtraso(1, "EXT-1");
        var segundo = politica.calcularAtraso(2, "EXT-1");

        assertThat(primeiro).isBetween(Duration.ofSeconds(5), Duration.ofSeconds(6));
        assertThat(segundo).isBetween(Duration.ofSeconds(10), Duration.ofSeconds(11));
    }

    @Test
    void deveEsgotarNoLimiteConfigurado() {
        assertThat(politica.esgotou(3)).isFalse();
        assertThat(politica.esgotou(4)).isTrue();
    }
}
