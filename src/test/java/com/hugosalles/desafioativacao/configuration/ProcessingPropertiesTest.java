package com.hugosalles.desafioativacao.configuration;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessingPropertiesTest {

    @Test
    void deveRejeitarTamanhoDeLoteForaDoLimite() {
        assertThatThrownBy(() -> new ProcessingProperties(
                4, Duration.ofSeconds(5), Duration.ofSeconds(2), Duration.ofMinutes(2), 0, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tamanho-lote");
    }
}
