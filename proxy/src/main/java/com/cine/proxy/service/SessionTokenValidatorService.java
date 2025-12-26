package com.cine.proxy.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class SessionTokenValidatorService {
    @Autowired
    private WebClient.Builder webClientBuilder;

    public boolean isSessionTokenValidRemoto(String token) {
        try {
            Map<String, String> req = Map.of("token", token);
            Map resp = webClientBuilder.build()
                .post()
                .uri("http://localhost:8080/validate-token")
                .bodyValue(req)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            return resp != null && Boolean.TRUE.equals(resp.get("valid"));
        } catch (Exception e) {
            System.out.println("Error al validar token en backend: " + e.getMessage());
            return false;
        }
    }
}