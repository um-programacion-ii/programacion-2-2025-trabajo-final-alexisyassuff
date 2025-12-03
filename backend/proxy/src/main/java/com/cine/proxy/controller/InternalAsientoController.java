package com.cine.proxy.controller;

import com.cine.proxy.model.Seat;
import com.cine.proxy.service.RedisSeatService;
import com.cine.proxy.service.MetricsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Endpoint interno para recibir notificaciones (mismo formato que Kafka).
 * Cambios principales:
 *  - usa seatService.upsertSeatWithTimestamp(...) para asegurar idempotencia / orden temporal
 *  - añade logs en INFO para facilitar trazabilidad
 *  - actualiza contadores en MetricsService
 */
@RestController
@RequestMapping("/internal")
public class InternalAsientoController {

    private static final Logger log = LoggerFactory.getLogger(InternalAsientoController.class);

    private final ObjectMapper mapper;
    private final RedisSeatService seatService;
    private final MetricsService metrics;

    public InternalAsientoController(ObjectMapper mapper, RedisSeatService seatService, MetricsService metrics) {
        this.mapper = mapper;
        this.seatService = seatService;
        this.metrics = metrics;
    }

    @PostMapping("/evento-asiento")
    public ResponseEntity<String> receiveEventoAsiento(@RequestBody JsonNode payload) {
        metrics.incrementEventsReceived(); // contador: recibidos
        try {
            String eventoId = payload.path("eventoId").asText();
            String asientoId = payload.path("asientoId").asText(null);
            String estado = payload.path("estado").asText(null);
            String usuario = payload.path("usuario").isNull() ? "" : payload.path("usuario").asText("");
            String timestamp = payload.path("timestamp").asText(null);

            if (eventoId == null || eventoId.isBlank() || asientoId == null || asientoId.isBlank()) {
                log.warn("Payload inválido: falta eventoId o asientoId - payload={}", payload.toString());
                return ResponseEntity.badRequest().body("eventoId y asientoId requeridos");
            }

            Instant updatedAt;
            try {
                updatedAt = (timestamp != null && !timestamp.isBlank()) ? Instant.parse(timestamp) : Instant.now();
            } catch (Exception e) {
                updatedAt = Instant.now();
            }

            Seat seat = new Seat(asientoId, estado == null ? "DESCONOCIDO" : estado, usuario, updatedAt);

            // ---- APLICAMOS LA CORRECCIÓN: upsert con chequeo por timestamp ----
            seatService.upsertSeatWithTimestamp(eventoId, seat);
            // ------------------------------------------------------------------

            metrics.incrementEventsProcessed(); // contador: procesados correctamente
            log.info("Internal endpoint processed: eventoId={} asientoId={} estado={} ts={}",
                    eventoId, asientoId, seat.getStatus(), seat.getUpdatedAt());
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.error("Error al procesar /internal/evento-asiento", e);
            return ResponseEntity.status(500).body("error: " + e.getMessage());
        }
    }
}