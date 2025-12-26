package com.example.proxy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Optional;

@Service
public class EventosKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(EventosKafkaListener.class);

    private final WebClient backendRestClient;

    public EventosKafkaListener(WebClient.Builder webClientBuilder) {
        this.backendRestClient = webClientBuilder.baseUrl("http://localhost:8080").build(); // URL de tu backend
    }

    @KafkaListener(
        topics = "${kafka.topic.eventos:eventos-actualizacion}",
        groupId = "${spring.kafka.consumer.group-id:proxy-group}"
    )
    public void onEventoChange(String raw) {
        try {
            backendRestClient.post()
                .uri("/api/eventos/sync/all")
                .retrieve()
                .toBodilessEntity()
                .block();
        } catch (Exception e) {
            log.error("Error notificando al backend para sync all eventos", e);
        }
    }
}