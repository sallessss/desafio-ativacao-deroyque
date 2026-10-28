package com.hugosalles.desafioativacao;

import com.hugosalles.desafioativacao.configuration.JwtProperties;
import com.hugosalles.desafioativacao.configuration.ProcessingProperties;
import com.hugosalles.desafioativacao.configuration.ProviderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({ProviderProperties.class, ProcessingProperties.class, JwtProperties.class})
public class DesafioAtivacaoApplication {

	@Bean
	Clock utcClock() {
		return Clock.systemUTC();
	}

	public static void main(String[] args) {
		SpringApplication.run(DesafioAtivacaoApplication.class, args);
	}

}
