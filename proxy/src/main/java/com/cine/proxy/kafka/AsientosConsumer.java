package com.cine.proxy.kafka;

import com.cine.proxy.model.Seat;
import com.cine.proxy.service.RedisSeatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AsientosConsumer {

    private static final Logger log = LoggerFactory.getLogger(AsientosConsumer.class);

    private final ObjectMapper mapper;
    private final RedisSeatService seatService;

    public AsientosConsumer(ObjectMapper mapper, RedisSeatService seatService) {
        this.mapper = mapper;
        this.seatService = seatService;
    }

    // Topic correcto: "eventos-asientos"
    @KafkaListener(topics = "eventos-asientos", groupId = "proxy-asientos-group")
    public void onMessage(String message) {
        try {
            JsonNode node = mapper.readTree(message);

            // payload real: {"eventoId":123,"asientoId":"C3","estado":"BLOQUEADO","usuario":"test","timestamp":"2025-11-29T18:00:00Z"}
            String eventoId = node.path("eventoId").asText();
            String asientoId = node.path("asientoId").asText(null);
            String estado = node.path("estado").asText(null);
            String usuario = node.path("usuario").isNull() ? null : node.path("usuario").asText(null);
            String timestamp = node.path("timestamp").asText(null);

            if (eventoId == null || asientoId == null) {
                log.warn("Mensaje Kafka inválido (falta eventoId o asientoId): {}", message);
                return;
            }

            Instant updatedAt = null;
            try {
                if (timestamp != null && !timestamp.isBlank()) {
                    updatedAt = Instant.parse(timestamp);
                } else {
                    updatedAt = Instant.now();
                }
            } catch (Exception e) {
                updatedAt = Instant.now();
            }

            Seat seat = new Seat(asientoId, estado == null ? "DESCONOCIDO" : estado, usuario == null ? "" : usuario, updatedAt);

            log.info("Procesando evento-asiento: eventoId={} asientoId={} estado={}", eventoId, asientoId, seat.getStatus());
            seatService.upsertSeat(eventoId, seat);
        } catch (Exception e) {
            log.error("Error procesando mensaje Kafka de eventos-asientos", e);
        }
    }
}
