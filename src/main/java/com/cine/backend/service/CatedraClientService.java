package com.cine.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class CatedraClientService {

    private final WebClient webClient;
    private final TokenService tokenService;

    public CatedraClientService(WebClient catedraWebClient, TokenService tokenService) {
        this.webClient = catedraWebClient;
        this.tokenService = tokenService;
    }

    /**
     * Llama al endpoint de la cátedra para registrar el backend.
     * Se asume que la respuesta contiene un campo "token".
     */
    public Map<String, Object> register(Map<String, Object> payload) {
        Map<String, Object> response = webClient.post()
                .uri("/api/v1/agregar_usuario")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response != null && response.containsKey("token")) {
            Object t = response.get("token");
            if (t instanceof String) {
                tokenService.saveToken((String) t, "catedra");
            }
        }

        return response;
    }

    /**
     * Método auxiliar para llamadas autenticadas (se usará más adelante).
     */
    public Map<String, Object> getWithAuth(String path) {
        String token = tokenService.getLatestToken("catedra")
                .map(et -> et.getToken())
                .orElse(null);

        return webClient.get()
                .uri(path)
                .headers(h -> { if (token != null) h.setBearerAuth(token); })
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }
}