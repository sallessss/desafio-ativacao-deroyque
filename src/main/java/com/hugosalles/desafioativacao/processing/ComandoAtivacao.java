package com.hugosalles.desafioativacao.processing;

public record ComandoAtivacao(
        String identificadorExterno,
        String identificadorCliente,
        String codigoProduto,
        String identificadorCorrelacao) {
}
