package com.cine.backend.service;

import com.cine.backend.catedra.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
public class CatedraClient {

    private static final Logger log = LoggerFactory.getLogger(CatedraClient.class);
    private final WebClient webClient;

    public CatedraClient(WebClient webClient, @Value("${catedra.base-url:http://localhost:8080}") String baseUrl) {
        this.webClient = webClient.mutate().baseUrl(baseUrl).build();
    }


    public TokenResponse callCatedraApi(Object payload) {
        try {
            log.info("Calling cátedra /api/v1/agregar_usuario with payload: {}", payload);
            Mono<TokenResponse> mono = webClient.post()
                    .uri("/api/v1/agregar_usuario")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(TokenResponse.class);

            TokenResponse resp = mono.block();
            log.info("Cátedra API response: {}", resp);
            return resp;
        } catch (WebClientResponseException we) {
            String body = we.getResponseBodyAsString();
            log.error("Cátedra returned error {} {}. Body: {}", we.getRawStatusCode(), we.getStatusText(), body);
            return null;
        } catch (Exception e) {
            log.error("Error calling cátedra: {}", e.toString(), e);
            return null;
        }
    }

    public String pingBaseUrl() {
        try {
            return webClient.get().uri("/catedra/health").retrieve().bodyToMono(String.class).block();
        } catch (Exception e) {
            log.warn("pingBaseUrl failed: {}", e.toString());
            return null;
        }
    }
}