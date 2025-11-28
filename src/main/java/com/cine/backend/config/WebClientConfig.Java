package com.cine.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${catedra.base-url:}")
    private String catedraBaseUrl;

    @Bean
    public WebClient catedraWebClient(WebClient.Builder builder) {
        if (catedraBaseUrl == null || catedraBaseUrl.isBlank()) {
            return builder.build();
        }
        return builder.baseUrl(catedraBaseUrl).build();
    }
}