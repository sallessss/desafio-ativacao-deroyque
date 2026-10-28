package com.hugosalles.desafioativacao.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "provedor")
public record ProviderProperties(
        String urlBase,
        String token,
        Duration timeoutConexao,
        Duration timeoutLeitura) {

    public ProviderProperties {
        if (urlBase == null || urlBase.isBlank()) {
            throw new IllegalArgumentException("provedor.url-base é obrigatório");
        }
        token = token == null ? "" : token;
        exigirDuracaoPositiva(timeoutConexao, "provedor.timeout-conexao");
        exigirDuracaoPositiva(timeoutLeitura, "provedor.timeout-leitura");
    }

    public Duration timeoutTotal() {
        return timeoutConexao.plus(timeoutLeitura);
    }

    private static void exigirDuracaoPositiva(Duration duracao, String propriedade) {
        if (duracao == null || duracao.isZero() || duracao.isNegative()) {
            throw new IllegalArgumentException(propriedade + " deve ser positiva");
        }
    }
}
