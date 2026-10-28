package com.hugosalles.desafioativacao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hugosalles.desafioativacao.entity.SolicitacaoAtivacao;
import com.hugosalles.desafioativacao.entity.StatusSolicitacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record SolicitacaoResponse(
        @Schema(description = "Identificador da solicitação", example = "0b7f3c1e-5d2a-4c4e-9a51-2f8d7b1e6c90")
        @JsonProperty("id") UUID id,
        @Schema(description = "Identificador único no sistema de origem", example = "EXT-12345")
        @JsonProperty("externalId") String identificadorExterno,
        @Schema(description = "Identificador do cliente", example = "CUST-10001")
        @JsonProperty("customerId") String identificadorCliente,
        @Schema(description = "Código do produto", example = "INTERNET-500")
        @JsonProperty("productCode") String codigoProduto,
        @Schema(description = "Estado atual da solicitação", example = "PENDING")
        @JsonProperty("status") StatusSolicitacao status,
        @Schema(description = "Data de criação em UTC", example = "2026-10-26T14:03:11Z")
        @JsonProperty("createdAt") Instant criadoEm,
        @Schema(description = "Data da última atualização em UTC", example = "2026-10-26T14:03:11Z")
        @JsonProperty("updatedAt") Instant atualizadoEm) {

    public static SolicitacaoResponse from(SolicitacaoAtivacao solicitacao) {
        return new SolicitacaoResponse(solicitacao.getId(), solicitacao.getIdentificadorExterno(),
                solicitacao.getIdentificadorCliente(), solicitacao.getCodigoProduto(), solicitacao.getStatus(),
                solicitacao.getCriadoEm(), solicitacao.getAtualizadoEm());
    }
}
