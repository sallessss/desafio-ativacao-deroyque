package com.hugosalles.desafioativacao.controller;

import com.hugosalles.desafioativacao.dto.CriarSolicitacaoRequest;
import com.hugosalles.desafioativacao.dto.PageResponse;
import com.hugosalles.desafioativacao.dto.ProblemaResponse;
import com.hugosalles.desafioativacao.dto.SolicitacaoResponse;
import com.hugosalles.desafioativacao.entity.StatusSolicitacao;
import com.hugosalles.desafioativacao.service.SolicitacaoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/requests")
@Tag(name = "Solicitações de ativação", description = "Criação e consulta de solicitações de ativação")
public class SolicitacaoController {

    private final SolicitacaoService service;

    public SolicitacaoController(SolicitacaoService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_requests.write')")
    @Operation(summary = "Criar solicitação", description = "Registra uma solicitação em PENDING para processamento assíncrono.",
            security = @SecurityRequirement(name = "oauth2", scopes = "requests.write"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Solicitação criada",
                    headers = @Header(name = "Location", description = "URI da solicitação criada"),
                    content = @Content(schema = @Schema(implementation = SolicitacaoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(schema = @Schema(implementation = ProblemaResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido",
                    content = @Content(schema = @Schema(implementation = ProblemaResponse.class))),
            @ApiResponse(responseCode = "403", description = "Escopo requests.write ausente",
                    content = @Content(schema = @Schema(implementation = ProblemaResponse.class))),
            @ApiResponse(responseCode = "409", description = "externalId duplicado",
                    content = @Content(schema = @Schema(implementation = ProblemaResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro inesperado",
                    content = @Content(schema = @Schema(implementation = ProblemaResponse.class)))
    })
    public ResponseEntity<SolicitacaoResponse> criarSolicitacao(@Valid @RequestBody CriarSolicitacaoRequest request, Authentication authentication) {
        var solicitacao = service.criarSolicitacao(request, authentication.getName());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(solicitacao.getId()).toUri();
        return ResponseEntity.created(location).body(SolicitacaoResponse.from(solicitacao));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_requests.read')")
    @Operation(summary = "Consultar solicitação", description = "Retorna a solicitação somente quando pertence ao consumidor autenticado.",
            security = @SecurityRequirement(name = "oauth2", scopes = "requests.read"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitação encontrada",
                    content = @Content(schema = @Schema(implementation = SolicitacaoResponse.class))),
            @ApiResponse(responseCode = "400", description = "UUID inválido",
                    content = @Content(schema = @Schema(implementation = ProblemaResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido",
                    content = @Content(schema = @Schema(implementation = ProblemaResponse.class))),
            @ApiResponse(responseCode = "403", description = "Escopo requests.read ausente",
                    content = @Content(schema = @Schema(implementation = ProblemaResponse.class))),
            @ApiResponse(responseCode = "404", description = "Solicitação inexistente ou de outro consumidor",
                    content = @Content(schema = @Schema(implementation = ProblemaResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro inesperado",
                    content = @Content(schema = @Schema(implementation = ProblemaResponse.class)))
    })
    public SolicitacaoResponse buscarSolicitacao(@PathVariable UUID id, Authentication authentication) {
        return SolicitacaoResponse.from(service.buscarSolicitacao(id, authentication.getName()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_requests.read')")
    @Operation(summary = "Listar solicitações", description = "Lista paginada do consumidor autenticado, com filtros opcionais.",
            security = @SecurityRequirement(name = "oauth2", scopes = "requests.read"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de solicitações"),
            @ApiResponse(responseCode = "400", description = "Filtro ou paginação inválidos",
                    content = @Content(schema = @Schema(implementation = ProblemaResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido",
                    content = @Content(schema = @Schema(implementation = ProblemaResponse.class))),
            @ApiResponse(responseCode = "403", description = "Escopo requests.read ausente",
                    content = @Content(schema = @Schema(implementation = ProblemaResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro inesperado",
                    content = @Content(schema = @Schema(implementation = ProblemaResponse.class)))
    })
    public PageResponse<SolicitacaoResponse> listarSolicitacoes(
            @RequestParam(required = false) StatusSolicitacao status,
            @RequestParam(name = "customerId", required = false)
            @Size(max = 64)
            @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "deve conter apenas letras, números, ponto, sublinhado ou hífen")
            String identificadorCliente,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication) {
        return PageResponse.from(service.listarSolicitacoes(
                status, identificadorCliente, page, size, authentication.getName()), SolicitacaoResponse::from);
    }
}
