package com.cine.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Servicio que procesa payloads de webhook de forma idempotente.
 * Añadido: logs de diagnóstico y verificación de lectura tras escritura.
 */
@Service
public class WebhookProcessingService {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessingService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    // Cola para reintentos en background (persistencia eventual).
    private final BlockingQueue<Runnable> retryQueue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor();

    public WebhookProcessingService(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @PostConstruct
    public void startRetryWorker() {
        retryExecutor.scheduleWithFixedDelay(this::drainAndProcess, 5, 5, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stopRetryWorker() {
        retryExecutor.shutdownNow();
    }

    @SuppressWarnings("unchecked")
    public void process(Map<String, Object> payload) {
        String eventoId = payload.get("eventoId") == null ? null : payload.get("eventoId").toString();
        String seatId = payload.get("seatId") == null ? null : payload.get("seatId").toString();
        String updatedAt = payload.get("updatedAt") == null ? null : payload.get("updatedAt").toString();

        if (eventoId == null || seatId == null) {
            throw new IllegalArgumentException("eventoId y seatId requeridos");
        }

        Instant incomingTs;
        try {
            incomingTs = updatedAt == null ? Instant.EPOCH : Instant.parse(updatedAt);
        } catch (Exception e) {
            incomingTs = Instant.now();
        }

        final String key = "backend:proxy:webhook:" + eventoId + ":" + seatId;
        final String valueStr = incomingTs.toString();
        final String eventoCopy = eventoId;
        final String seatCopy = seatId;

        // Log info de la factory (diagnóstico)
        try {
            if (redis.getConnectionFactory() instanceof LettuceConnectionFactory) {
                LettuceConnectionFactory lcf = (LettuceConnectionFactory) redis.getConnectionFactory();
                log.debug("Redis connection factory info: host={}, port={}, db={}", lcf.getHostName(), lcf.getPort(), lcf.getDatabase());
            } else if (redis.getConnectionFactory() != null) {
                log.debug("Redis ConnectionFactory class: {}", redis.getConnectionFactory().getClass().getName());
            } else {
                log.debug("Redis ConnectionFactory is null");
            }
        } catch (Exception ex) {
            log.warn("No se pudo obtener info de la RedisConnectionFactory: {}", ex.toString());
        }

        // Intentos síncronos
        int maxAttempts = 3;
        long[] backoffs = new long[]{100, 300, 1000}; // ms
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // Leer existente
                String existing = redis.opsForValue().get(key);
                Instant existingTs = existing == null ? Instant.EPOCH : Instant.parse(existing);

                if (incomingTs.isBefore(existingTs)) {
                    log.info("Webhook entrante ignorado por timestamp antiguo: eventoId={} seatId={} incoming={} existing={}",
                            eventoId, seatId, incomingTs, existingTs);
                    return;
                }

                // Log de lo que vamos a escribir
                log.debug("Intentando escribir en Redis: key='{}' value='{}' (attempt {}/{})", key, valueStr, attempt, maxAttempts);

                // Guardar nuevo timestamp
                redis.opsForValue().set(key, valueStr);

                // Verificación inmediata de lectura
                try {
                    String readBack = redis.opsForValue().get(key);
                    log.debug("Lectura de verificación tras set: key='{}' readBack='{}'", key, readBack);
                } catch (Exception readEx) {
                    log.warn("No se pudo leer clave justo después de set para verificación: {}", readEx.toString());
                }

                log.info("Webhook procesado y marcado: eventoId={} seatId={} ts={}", eventoId, seatId, incomingTs);
                return;
            } catch (Exception ex) {
                log.warn("Intento {}/{}: fallo al persistir webhook en Redis (eventoId={}, seatId={}). Error: {}",
                        attempt, maxAttempts, eventoId, seatId, ex.toString());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(backoffs[attempt - 1]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    enqueueRetry(() -> retryPersist(key, valueStr, eventoCopy, seatCopy));
                    log.warn("Se encoló webhook para reintento en background: eventoId={}, seatId={}", eventoId, seatId);
                }
            }
        }
    }

    private void enqueueRetry(Runnable task) {
        try {
            retryQueue.offer(task, 200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("No se pudo encolar tarea de reintento: {}", e.toString());
        }
    }

    private void drainAndProcess() {
        Runnable task;
        while ((task = retryQueue.poll()) != null) {
            try {
                task.run();
            } catch (Exception ex) {
                log.warn("Error al procesar reintento en background: {}", ex.toString());
                enqueueRetry(task);
                break;
            }
        }
    }

    private void retryPersist(String key, String value, String eventoId, String seatId) {
        try {
            redis.opsForValue().set(key, value);
            log.info("Reintento background exitoso: eventoId={} seatId={} key={}", eventoId, seatId, key);
        } catch (Exception ex) {
            log.warn("Reintento background falló para key {}: {}", key, ex.toString());
            throw ex;
        }
    }
}