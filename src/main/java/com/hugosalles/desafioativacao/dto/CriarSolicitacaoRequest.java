package com.hugosalles.desafioativacao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CriarSolicitacaoRequest(
        @Schema(description = "Identificador único da solicitação no sistema de origem", example = "EXT-12345")
        @JsonProperty("externalId") @NotBlank(message = "é obrigatório") @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "deve conter apenas letras, números, ponto, sublinhado ou hífen")
        String identificadorExterno,
        @Schema(description = "Identificador do cliente", example = "CUST-10001")
        @JsonProperty("customerId") @NotBlank(message = "é obrigatório") @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "deve conter apenas letras, números, ponto, sublinhado ou hífen")
        String identificadorCliente,
        @Schema(description = "Código do produto que será ativado", example = "INTERNET-500")
        @JsonProperty("productCode") @NotBlank(message = "é obrigatório") @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "deve conter apenas letras, números, ponto, sublinhado ou hífen")
        String codigoProduto) {
}
