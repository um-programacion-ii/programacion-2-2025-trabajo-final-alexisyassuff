package com.cine.proxy.controller;

import com.cine.proxy.model.Seat;
import com.cine.proxy.service.RedisSeatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@RestController
@RequestMapping("/internal")
public class InternalAsientoController {

    private final ObjectMapper mapper;
    private final RedisSeatService seatService;

    public InternalAsientoController(ObjectMapper mapper, RedisSeatService seatService) {
        this.mapper = mapper;
        this.seatService = seatService;
    }

    @PostMapping("/evento-asiento")
    public ResponseEntity<String> receiveEventoAsiento(@RequestBody JsonNode payload) {
        try {
            String eventoId = payload.path("eventoId").asText();
            String asientoId = payload.path("asientoId").asText(null);
            String estado = payload.path("estado").asText(null);
            String usuario = payload.path("usuario").isNull() ? "" : payload.path("usuario").asText("");
            String timestamp = payload.path("timestamp").asText(null);

            if (eventoId == null || eventoId.isBlank() || asientoId == null || asientoId.isBlank()) {
                return ResponseEntity.badRequest().body("eventoId y asientoId requeridos");
            }

            Instant updatedAt;
            try {
                updatedAt = (timestamp != null && !timestamp.isBlank()) ? Instant.parse(timestamp) : Instant.now();
            } catch (Exception e) {
                updatedAt = Instant.now();
            }

            Seat seat = new Seat(asientoId, estado == null ? "DESCONOCIDO" : estado, usuario, updatedAt);
            seatService.upsertSeat(eventoId, seat);
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("error: " + e.getMessage());
        }
    }
}