package com.cine.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * WebhookProcessingService - versión que delega reintentos persistentes en RedisRetryQueueService.
 * - process(payloadMap): intenta persistir (3 intentos). Si falla, serializa payload y pushRetry(...) en Redis.
 * - processRetryPayload(json): deserializa y reintenta el mismo procesamiento.
 */
@Service
public class WebhookProcessingService {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessingService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final RedisRetryQueueService retryQueue;

    public WebhookProcessingService(StringRedisTemplate redis, ObjectMapper mapper, RedisRetryQueueService retryQueue) {
        this.redis = redis;
        this.mapper = mapper;
        this.retryQueue = retryQueue;
    }

    @SuppressWarnings("unchecked")
    public void process(Map<String, Object> payload) {
        String eventoId = payload.get("eventoId").toString();
        String seatId = payload.get("seatId").toString();
        String updatedAt = payload.getOrDefault("updatedAt", Instant.now().toString()).toString();

        String key = "backend:proxy:webhook:" + eventoId + ":" + seatId;
        String valueStr = updatedAt;

        int attempts = 3;
        long[] backoffs = new long[] {100, 300, 1000};
        for (int i=1;i<=attempts;i++) {
            try {
                // idempotency: compare timestamps
                String existing = redis.opsForValue().get(key);
                Instant existingTs = existing == null ? Instant.EPOCH : Instant.parse(existing);
                Instant incoming = Instant.parse(valueStr);
                if (incoming.isBefore(existingTs)) {
                    log.info("Ignorado por timestamp: {} < {}", incoming, existingTs);
                    return;
                }
                redis.opsForValue().set(key, valueStr);
                log.info("Webhook procesado y marcado: eventoId={} seatId={} ts={}", eventoId, seatId, valueStr);
                return;
            } catch (Exception ex) {
                log.warn("Intento {}/{} fallo al persistir en Redis: {}", i, attempts, ex.toString());
                try { Thread.sleep(backoffs[Math.min(i-1, backoffs.length-1)]); } catch (InterruptedException ignored) {}
            }
        }

        // si llegamos acá => persistencia fallida. Encolamos el payload serializado para reintento durable
        try {
            String json = mapper.writeValueAsString(payload);
            retryQueue.pushRetry(json);
            log.warn("Payload encolado en Redis para reintento durable: eventoId={} seatId={}", eventoId, seatId);
        } catch (Exception e) {
            log.error("No se pudo serializar payload para encolar: {}", e.toString());
        }
    }

    // Invocado por RedisRetryQueueService cuando saca un elemento de la lista
    public void processRetryPayload(String jsonPayload) throws Exception {
        Map<String,Object> payload = mapper.readValue(jsonPayload, Map.class);
        process(payload);
    }
}