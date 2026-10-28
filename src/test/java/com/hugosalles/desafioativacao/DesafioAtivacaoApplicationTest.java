package com.hugosalles.desafioativacao;

import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DesafioAtivacaoApplicationTest {

	@Test
	void deveFornecerRelogioUtc() {
		assertThat(new DesafioAtivacaoApplication().utcClock().getZone()).isEqualTo(ZoneOffset.UTC);
	}

}
