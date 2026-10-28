package com.hugosalles.desafioativacao.processing;

import com.hugosalles.desafioativacao.configuration.ProcessingProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PoliticaRetentativa {

    private final int maximoTentativas;
    private final Duration atrasoBase;

    public PoliticaRetentativa(ProcessingProperties properties) {
        this.maximoTentativas = properties.maximoTentativas();
        this.atrasoBase = properties.atrasoBase();
    }

    public boolean esgotou(int tentativas) {
        return tentativas >= maximoTentativas;
    }

    public Duration calcularAtraso(int tentativas, String chave) {
        long multiplicador = 1L << Math.min(Math.max(tentativas - 1, 0), 10);
        long jitter = Math.floorMod(chave.hashCode() * 31L + tentativas * 137L, 1000L);
        return atrasoBase.multipliedBy(multiplicador).plusMillis(jitter);
    }
}
