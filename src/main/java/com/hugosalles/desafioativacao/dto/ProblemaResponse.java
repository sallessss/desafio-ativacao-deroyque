package com.hugosalles.desafioativacao.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.util.List;

@Schema(description = "Erro padronizado no formato Problem Details")
public record ProblemaResponse(
        @JsonProperty("type") URI tipo,
        @JsonProperty("title") String titulo,
        @JsonProperty("status") int status,
        @JsonProperty("detail") String detalhe,
        @JsonProperty("instance") URI instancia,
        @JsonProperty("traceId") String identificadorRastreio,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("errors") List<ErroValidacaoResponse> erros) {

    public record ErroValidacaoResponse(
            @JsonProperty("field") String campo,
            @JsonProperty("message") String mensagem) {
    }
}
