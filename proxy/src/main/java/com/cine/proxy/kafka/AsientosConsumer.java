package com.cine.proxy.kafka;

import com.cine.proxy.model.Seat;
import com.cine.proxy.service.RedisSeatService;
import com.cine.proxy.service.MetricsService;
import com.cine.proxy.webhook.WebhookNotifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Kafka consumer para el topic configurado en application.yml (kafka.topic.eventos).
 * Ahora notifica al Backend vía WebhookNotifier luego de persistir/upsert en Redis.
 */
@Component
public class AsientosConsumer {

    private static final Logger log = LoggerFactory.getLogger(AsientosConsumer.class);

    private final ObjectMapper mapper;
    private final RedisSeatService seatService;
    private final MetricsService metrics;
    private final WebhookNotifier webhookNotifier;

    public AsientosConsumer(ObjectMapper mapper, RedisSeatService seatService, MetricsService metrics, WebhookNotifier webhookNotifier) {
        this.mapper = mapper;
        this.seatService = seatService;
        this.metrics = metrics;
        this.webhookNotifier = webhookNotifier;
    }

    @KafkaListener(topics = "${kafka.topic.eventos:eventos-asientos}", groupId = "${kafka.group:proxy-asientos-group}")
    public void onMessage(String message) {
        metrics.incrementEventsReceived();
        try {
            JsonNode node = mapper.readTree(message);

            String eventoId = node.path("eventoId").asText(null);
            String asientoId = node.path("asientoId").asText(null);
            String estado = node.path("estado").asText(null);
            String usuario = node.path("usuario").isNull() ? "" : node.path("usuario").asText("");
            String timestamp = node.path("timestamp").asText(null);

            if (eventoId == null || asientoId == null) {
                log.warn("Mensaje Kafka inválido (falta eventoId o asientoId): {}", message);
                return;
            }

            Instant updatedAt;
            try {
                updatedAt = (timestamp != null && !timestamp.isBlank()) ? Instant.parse(timestamp) : Instant.now();
            } catch (Exception e) {
                updatedAt = Instant.now();
            }

            Seat seat = new Seat(asientoId, estado == null ? "DESCONOCIDO" : estado, usuario, updatedAt);

            log.info("Procesando evento-asiento (kafka): eventoId={} asientoId={} estado={} ts={}",
                    eventoId, asientoId, seat.getStatus(), seat.getUpdatedAt());

            // Persistir con lógica de timestamp
            seatService.upsertSeatWithTimestamp(eventoId, seat);

            // NOTIFICAR al backend (no bloqueante; errores logged)
            try {
                webhookNotifier.notifySeatChange(eventoId, seat);
            } catch (Exception e) {
                log.warn("Error al invocar WebhookNotifier (no crítico): {}", e.getMessage());
            }

            metrics.incrementEventsProcessed();
        } catch (Exception e) {
            log.error("Error procesando mensaje Kafka de eventos-asientos", e);
        }
    }
}