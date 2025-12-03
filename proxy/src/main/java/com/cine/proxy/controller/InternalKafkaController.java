package com.cine.proxy.controller;

import com.cine.proxy.service.KafkaProducerService;
import com.cine.proxy.service.RedisSeatService;
import com.cine.proxy.model.Seat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoints internos para pruebas con Kafka/Redis.
 * - POST /internal/kafka/publish  -> publica payload al topic (útil para pruebas)
 * - GET  /internal/kafka/events?eventoId=1 -> devuelve los asientos persistidos para ese evento
 * - DELETE /internal/kafka/events?eventoId=1 -> borra la estructura de asientos del evento (opcional)
 *
 * Nota: GET devuelve directamente lo que guarda RedisSeatService (evento:<id>:asientos).
 */
@RestController
@RequestMapping("/internal/kafka")
public class InternalKafkaController {

    private static final Logger log = LoggerFactory.getLogger(InternalKafkaController.class);

    private final RedisSeatService seatService;
    private final KafkaProducerService producer;

    @Value("${kafka.topic.eventos:eventos-asientos}")
    private String defaultTopic;

    public InternalKafkaController(RedisSeatService seatService, KafkaProducerService producer) {
        this.seatService = seatService;
        this.producer = producer;
    }

    /**
     * Publica payload raw (JSON) al topic indicado o al topic por defecto.
     */
    @PostMapping("/publish")
    public ResponseEntity<?> publishToKafka(@RequestParam(required = false) String topic,
                                            @RequestBody String payload,
                                            @RequestParam(name = "timeoutMs", required = false, defaultValue = "3000") long timeoutMs) {
        String t = (topic == null || topic.isBlank()) ? defaultTopic : topic;
        try {
            producer.send(t, payload, timeoutMs);
            return ResponseEntity.ok(Map.of("sent", true, "topic", t));
        } catch (Exception e) {
            log.error("Error al publicar en Kafka topic={}: {}", t, e.getMessage());
            return ResponseEntity.status(502).body(Map.of("sent", false, "error", e.getMessage()));
        }
    }

    /**
     * Devuelve los asientos guardados para un evento (usa RedisSeatService).
     * Ejemplo: GET /internal/kafka/events?eventoId=1
     */
    @GetMapping("/events")
    public ResponseEntity<?> getEventsForEvento(@RequestParam(name = "eventoId") String eventoId) {
        if (eventoId == null || eventoId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "eventoId query param is required"));
        }
        List<Seat> seats = seatService.getSeatsForEvento(eventoId);
        return ResponseEntity.ok(seats);
    }

}