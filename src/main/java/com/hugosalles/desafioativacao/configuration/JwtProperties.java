package com.hugosalles.desafioativacao.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seguranca.jwt")
public record JwtProperties(String emissor, String audiencia) {

    public JwtProperties {
        if (emissor == null || emissor.isBlank()) {
            throw new IllegalArgumentException("seguranca.jwt.emissor é obrigatório");
        }
        if (audiencia == null || audiencia.isBlank()) {
            throw new IllegalArgumentException("seguranca.jwt.audiencia é obrigatória");
        }
    }
}
