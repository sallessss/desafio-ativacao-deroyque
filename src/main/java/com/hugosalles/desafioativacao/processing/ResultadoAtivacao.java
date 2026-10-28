package com.hugosalles.desafioativacao.processing;

import java.time.Duration;

public record ResultadoAtivacao(TipoResultadoAtivacao tipo, Duration atrasoSugerido) {

    public static final ResultadoAtivacao SUCESSO = new ResultadoAtivacao(TipoResultadoAtivacao.SUCESSO, null);
    public static final ResultadoAtivacao FALHA_TEMPORARIA =
            new ResultadoAtivacao(TipoResultadoAtivacao.FALHA_TEMPORARIA, null);
    public static final ResultadoAtivacao FALHA_PERMANENTE =
            new ResultadoAtivacao(TipoResultadoAtivacao.FALHA_PERMANENTE, null);

    public ResultadoAtivacao {
        if (tipo == null) throw new IllegalArgumentException("tipo é obrigatório");
        if (atrasoSugerido != null && atrasoSugerido.isNegative()) {
            throw new IllegalArgumentException("atraso sugerido não pode ser negativo");
        }
        if (tipo != TipoResultadoAtivacao.FALHA_TEMPORARIA && atrasoSugerido != null) {
            throw new IllegalArgumentException("somente falha temporária aceita atraso sugerido");
        }
    }

    public static ResultadoAtivacao falhaTemporaria(Duration atrasoSugerido) {
        return atrasoSugerido == null ? FALHA_TEMPORARIA
                : new ResultadoAtivacao(TipoResultadoAtivacao.FALHA_TEMPORARIA, atrasoSugerido);
    }

    public String name() {
        return tipo.name();
    }

    public enum TipoResultadoAtivacao {
        SUCESSO,
        FALHA_TEMPORARIA,
        FALHA_PERMANENTE
    }
}
