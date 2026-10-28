package com.hugosalles.desafioativacao.configuration;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class HttpConfiguration {

    @Bean
    RestClientCustomizer providerTimeouts(ProviderProperties providerProperties,
                                          ProcessingProperties processingProperties) {
        if (processingProperties.timeoutPreso().compareTo(providerProperties.timeoutTotal()) <= 0) {
            throw new IllegalStateException(
                    "processamento.timeout-preso deve ser maior que os timeouts combinados do provedor");
        }
        return builder -> {
            var requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(providerProperties.timeoutConexao());
            requestFactory.setReadTimeout(providerProperties.timeoutLeitura());
            builder.requestFactory(requestFactory);
        };
    }
}
