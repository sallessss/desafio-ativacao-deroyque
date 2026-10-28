package com.hugosalles.desafioativacao.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    static final String ESQUEMA_OAUTH2 = "oauth2";

    @Bean
    OpenAPI openApi(@Value("${documentacao.oauth2.token-url}") String urlToken) {
        var escopos = new Scopes()
                .addString("requests.read", "Consultar e listar solicitações")
                .addString("requests.write", "Criar solicitações");
        var fluxo = new OAuthFlow().tokenUrl(urlToken).scopes(escopos);
        var esquema = new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .description("OAuth 2.0 Client Credentials")
                .flows(new OAuthFlows().clientCredentials(fluxo));

        return new OpenAPI()
                .info(new Info()
                        .title("API de ativação de produtos")
                        .description("Registra, consulta e processa solicitações de ativação de produtos.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(ESQUEMA_OAUTH2, esquema))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_OAUTH2));
    }
}
