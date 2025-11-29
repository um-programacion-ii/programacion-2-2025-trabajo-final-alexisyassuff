package com.cine.proxy.service;

import com.cine.proxy.model.Seat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                Seat fallback = new Seat(field.toString(), value.toString(), "", Instant.now());
                seats.add(fallback);
            }
        });
        return seats;
    }

    /**
     * Inserta o actualiza un asiento dentro del hash del evento.
     * field = seatId, value = JSON del Seat
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
}
