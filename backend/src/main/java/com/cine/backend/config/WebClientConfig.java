package com.cine.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * Bean WebClient disponible para inyección en los servicios.
     * Usa catedra.base-url como base por defecto.
     */
    @Bean
    public WebClient webClient(WebClient.Builder builder,
                               @Value("${catedra.base-url:http://localhost:8080}") String catedraBaseUrl) {
        return builder
                .baseUrl(catedraBaseUrl)
                .build();
    }
}
