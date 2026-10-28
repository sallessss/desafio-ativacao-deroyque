package com.hugosalles.desafioativacao.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "processamento")
public record ProcessingProperties(
        int maximoTentativas,
        Duration atrasoBase,
        Duration intervaloWorker,
        Duration timeoutPreso,
        int tamanhoLote,
        boolean workerHabilitado) {

    public ProcessingProperties {
        if (maximoTentativas < 1) {
            throw new IllegalArgumentException("processamento.maximo-tentativas deve ser maior que zero");
        }
        exigirDuracaoPositiva(atrasoBase, "processamento.atraso-base");
        exigirDuracaoPositiva(intervaloWorker, "processamento.intervalo-worker");
        exigirDuracaoPositiva(timeoutPreso, "processamento.timeout-preso");
        if (tamanhoLote < 1 || tamanhoLote > 1_000) {
            throw new IllegalArgumentException("processamento.tamanho-lote deve estar entre 1 e 1000");
        }
    }

    private static void exigirDuracaoPositiva(Duration duracao, String propriedade) {
        if (duracao == null || duracao.isZero() || duracao.isNegative()) {
            throw new IllegalArgumentException(propriedade + " deve ser positiva");
        }
    }
}
