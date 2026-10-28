package com.hugosalles.desafioativacao.controller;

import com.hugosalles.desafioativacao.configuration.SecurityConfiguration;
import com.hugosalles.desafioativacao.entity.SolicitacaoAtivacao;
import com.hugosalles.desafioativacao.exception.SolicitacaoDuplicadaException;
import com.hugosalles.desafioativacao.exception.SolicitacaoNaoEncontradaException;
import com.hugosalles.desafioativacao.service.SolicitacaoService;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SolicitacaoController.class)
@Import(SecurityConfiguration.class)
class SolicitacaoControllerTest {

    private static final Instant AGORA = Instant.parse("2026-10-28T12:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SolicitacaoService service;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    Tracer tracer;

    @Test
    void deveCriarComContratoPreservado() throws Exception {
        var solicitacao = SolicitacaoAtivacao.criar("EXT-12345", "CUST-10001", "INTERNET-500", "cliente-a", AGORA);
        when(service.criarSolicitacao(any(), eq("cliente-a"))).thenReturn(solicitacao);

        mockMvc.perform(post("/api/v1/requests")
                        .with(jwt().jwt(token -> token.subject("cliente-a")).authorities(() -> "SCOPE_requests.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalId":"EXT-12345","customerId":"CUST-10001","productCode":"INTERNET-500"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/requests/" + solicitacao.getId()))
                .andExpect(jsonPath("$.externalId").value("EXT-12345"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-28T12:00:00Z"));
    }

    @Test
    void deveRetornarProblemaDeValidacao() throws Exception {
        mockMvc.perform(post("/api/v1/requests")
                        .with(jwt().authorities(() -> "SCOPE_requests.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"\",\"customerId\":\"CUST-1\",\"productCode\":\"PROD-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:desafio-ativacao:problem:validation-error"))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.errors[0].field").value("externalId"));
    }

    @Test
    void deveRetornarNaoEncontradaSemRevelarOutroConsumidor() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.buscarSolicitacao(id, "cliente-a")).thenThrow(new SolicitacaoNaoEncontradaException());

        mockMvc.perform(get("/api/v1/requests/{id}", id)
                        .with(jwt().jwt(token -> token.subject("cliente-a")).authorities(() -> "SCOPE_requests.read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Solicitação não encontrada"));
    }

    @Test
    void deveConsultarSolicitacaoExistenteComContratoPreservado() throws Exception {
        var solicitacao = SolicitacaoAtivacao.criar(
                "EXT-12345", "CUST-10001", "INTERNET-500", "cliente-a", AGORA);
        when(service.buscarSolicitacao(solicitacao.getId(), "cliente-a")).thenReturn(solicitacao);

        mockMvc.perform(get("/api/v1/requests/{id}", solicitacao.getId())
                        .with(jwt().jwt(token -> token.subject("cliente-a"))
                                .authorities(() -> "SCOPE_requests.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(solicitacao.getId().toString()))
                .andExpect(jsonPath("$.externalId").value("EXT-12345"))
                .andExpect(jsonPath("$.customerId").value("CUST-10001"))
                .andExpect(jsonPath("$.productCode").value("INTERNET-500"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-28T12:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-28T12:00:00Z"));
    }

    @Test
    void deveRetornarConflitoParaDuplicada() throws Exception {
        when(service.criarSolicitacao(any(), any())).thenThrow(new SolicitacaoDuplicadaException());

        mockMvc.perform(post("/api/v1/requests")
                        .with(jwt().authorities(() -> "SCOPE_requests.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"EXT-1\",\"customerId\":\"CUST-1\",\"productCode\":\"PROD-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Solicitação duplicada"));
    }

    @Test
    void deveExigirJwt() throws Exception {
        mockMvc.perform(get("/api/v1/requests/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.type").value("urn:desafio-ativacao:problem:security"))
                .andExpect(jsonPath("$.title").value("Não autenticado"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void deveRecusarEscopoIncorreto() throws Exception {
        mockMvc.perform(get("/api/v1/requests/" + UUID.randomUUID())
                .with(jwt().authorities(() -> "SCOPE_requests.write")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acesso negado"));
    }

    @Test
    void deveRejeitarUuidInvalidoComProblemaPadronizado() throws Exception {
        mockMvc.perform(get("/api/v1/requests/invalido")
                        .with(jwt().authorities(() -> "SCOPE_requests.read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Entrada inválida"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void deveListarComFiltrosEPaginacao() throws Exception {
        var solicitacao = SolicitacaoAtivacao.criar("EXT-1", "CUST-1", "PROD-1", "cliente-a", AGORA);
        when(service.listarSolicitacoes(eq(com.hugosalles.desafioativacao.entity.StatusSolicitacao.PENDING),
                eq("CUST-1"), eq(0), eq(20), eq("cliente-a")))
                .thenReturn(new PageImpl<>(List.of(solicitacao), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/requests?status=PENDING&customerId=CUST-1")
                        .with(jwt().jwt(token -> token.subject("cliente-a")).authorities(() -> "SCOPE_requests.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].externalId").value("EXT-1"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void deveOcultarDetalhesDeErroInesperado() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.buscarSolicitacao(id, "cliente-a")).thenThrow(new IllegalStateException("segredo interno"));

        mockMvc.perform(get("/api/v1/requests/{id}", id)
                        .with(jwt().jwt(token -> token.subject("cliente-a")).authorities(() -> "SCOPE_requests.read")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("urn:desafio-ativacao:problem:internal-error"))
                .andExpect(jsonPath("$.title").value("Erro interno"))
                .andExpect(jsonPath("$.detail").value("Não foi possível processar a requisição."))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void deveRejeitarJsonMalformadoComoEntradaInvalida() throws Exception {
        mockMvc.perform(post("/api/v1/requests")
                        .with(jwt().authorities(() -> "SCOPE_requests.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Entrada inválida"));
    }

    @Test
    void deveLimitarTamanhoDaPagina() throws Exception {
        mockMvc.perform(get("/api/v1/requests?size=101")
                        .with(jwt().authorities(() -> "SCOPE_requests.read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Entrada inválida"));
    }

    @Test
    void deveValidarFiltroDeCliente() throws Exception {
        mockMvc.perform(get("/api/v1/requests?customerId=cliente%20invalido")
                        .with(jwt().authorities(() -> "SCOPE_requests.read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Entrada inválida"));
    }
}
