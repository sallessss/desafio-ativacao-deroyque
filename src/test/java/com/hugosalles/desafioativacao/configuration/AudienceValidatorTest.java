package com.hugosalles.desafioativacao.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator("desafio-ativacao");

    @Test
    void deveAceitarAudienciaEsperada() {
        assertThat(validator.validate(jwt(List.of("desafio-ativacao"))).hasErrors()).isFalse();
    }

    @Test
    void deveRecusarAudienciaDiferente() {
        assertThat(validator.validate(jwt(List.of("outro-servico"))).hasErrors()).isTrue();
    }

    private Jwt jwt(List<String> audiences) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject("cliente")
                .audience(audiences).issuedAt(Instant.EPOCH).expiresAt(Instant.MAX).build();
    }
}
