package com.cine.proxy.service;

import com.cine.proxy.model.Seat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Servicio para leer/escribir asientos en Redis.
 * Añadimos upsertSeatWithTimestamp() para idempotencia/orden temporal.
 */
@Service
public class RedisSeatService {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisSeatService(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    private String keyForEvento(String eventoId) {
        return "evento:" + eventoId + ":asientos";
    }

    public List<Seat> getSeatsForEvento(String eventoId) {
        String key = keyForEvento(eventoId);
        Map<Object, Object> entries = redis.opsForHash().entries(key);
        List<Seat> seats = new ArrayList<>();
        if (entries == null || entries.isEmpty()) return seats;

        entries.forEach((field, value) -> {
            try {
                String json = value.toString();
                Seat s = mapper.readValue(json, Seat.class);
                seats.add(s);
            } catch (Exception e) {
                // fallback: crear Seat mínimo usando constructor de 4 argumentos
                Seat fallback = new Seat(field.toString(), value.toString(), "", Instant.now());
                seats.add(fallback);
            }
        });
        return seats;
    }

    /**
     * Inserta o actualiza un asiento dentro del hash del evento.
     */
    public void upsertSeat(String eventoId, Seat seat) {
        try {
            String key = keyForEvento(eventoId);
            String json = mapper.writeValueAsString(seat);
            redis.opsForHash().put(key, seat.getSeatId(), json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upsert seat in Redis", e);
        }
    }

    /**
     * Upsert con lógica de idempotencia por timestamp:
     * - Si no existe: escribe el seat.
     * - Si existe: compara updatedAt y solo escribe si incoming.updatedAt >= existing.updatedAt.
     *
     * Implementación simple GET + PUT (suficiente para pruebas). Para alta concurrencia
     * puede implementarse en LUA para atomicidad.
     */
    public void upsertSeatWithTimestamp(String eventoId, Seat incoming) {
        try {
            String key = keyForEvento(eventoId);
            String field = incoming.getSeatId();

            Object existingObj = redis.opsForHash().get(key, field);
            if (existingObj == null) {
                String json = mapper.writeValueAsString(incoming);
                redis.opsForHash().put(key, field, json);
                return;
            }

            String existingJson = existingObj.toString();
            Seat existing = null;
            try {
                existing = mapper.readValue(existingJson, Seat.class);
            } catch (Exception ex) {
                existing = null; // si no parsea, sobreescribimos
            }

            if (existing == null) {
                String json = mapper.writeValueAsString(incoming);
                redis.opsForHash().put(key, field, json);
                return;
            }

            java.time.Instant existingTs = existing.getUpdatedAt() == null ? Instant.EPOCH : existing.getUpdatedAt();
            java.time.Instant incomingTs = incoming.getUpdatedAt() == null ? Instant.EPOCH : incoming.getUpdatedAt();

            // ONLY WRITE if incomingTs >= existingTs (no sobrescribir con eventos antiguos)
            if (!incomingTs.isBefore(existingTs)) {
                String json = mapper.writeValueAsString(incoming);
                redis.opsForHash().put(key, field, json);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to upsert seat with timestamp in Redis", e);
        }
    }
}
