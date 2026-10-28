package com.hugosalles.desafioativacao.exception;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_TYPE_PREFIX = "urn:desafio-ativacao:problem:";

    private final Tracer tracer;

    public GlobalExceptionHandler(Tracer tracer) {
        this.tracer = tracer;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(externalName(error.getField()), error.getDefaultMessage())).toList();
        var problem = createProblem(HttpStatus.BAD_REQUEST, "Dados inválidos",
                "Um ou mais campos não passaram na validação.", request, "validation-error");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class, HttpMessageNotReadableException.class})
    ProblemDetail handleInvalidInput(Exception exception, HttpServletRequest request) {
        return createProblem(HttpStatus.BAD_REQUEST, "Entrada inválida", "Um parâmetro informado é inválido.",
                request, "invalid-input");
    }

    @ExceptionHandler(SolicitacaoNaoEncontradaException.class)
    ProblemDetail handleNotFound(HttpServletRequest request) {
        return createProblem(HttpStatus.NOT_FOUND, "Solicitação não encontrada",
                "A solicitação informada não existe.", request, "request-not-found");
    }

    @ExceptionHandler(SolicitacaoDuplicadaException.class)
    ProblemDetail handleDuplicate(HttpServletRequest request) {
        return createProblem(HttpStatus.CONFLICT, "Solicitação duplicada",
                "Já existe uma solicitação com o externalId informado.", request, "duplicate-request");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(HttpServletRequest request) {
        return createProblem(HttpStatus.FORBIDDEN, "Acesso negado",
                "O token não possui a permissão necessária.", request, "security");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        LOG.error("Erro inesperado ao processar URI={} traceId={}", request.getRequestURI(), traceId(), exception);
        return createProblem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Não foi possível processar a requisição.", request, "internal-error");
    }

    private ProblemDetail createProblem(HttpStatus status, String title, String detail, HttpServletRequest request, String type) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(PROBLEM_TYPE_PREFIX + type));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("traceId", traceId());
        return problem;
    }

    private String traceId() {
        var span = tracer.currentSpan();
        return span == null ? "unavailable" : span.context().traceId();
    }

    private String externalName(String internalName) {
        return switch (internalName) {
            case "identificadorExterno" -> "externalId";
            case "identificadorCliente" -> "customerId";
            case "codigoProduto" -> "productCode";
            default -> internalName;
        };
    }

    public record FieldError(String field, String message) {
    }
}
