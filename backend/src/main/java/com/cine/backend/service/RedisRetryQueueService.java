package com.cine.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cola de reintentos persistente en Redis.
 * - pushRetry(payload) hace LPUSH a la lista "backend:webhook:retry"
 * - worker procesa con rightPop(timeout) en background y llama a WebhookProcessingService.processRetryPayload(payload)
 *
 * Nota: usamos ObjectProvider<WebhookProcessingService> para obtener el bean de forma perezosa y evitar
 * dependencias constructor-as constructor circulares entre los servicios.
 */
@Service
public class RedisRetryQueueService {

    private static final Logger log = LoggerFactory.getLogger(RedisRetryQueueService.class);
    private static final String RETRY_LIST = "backend:webhook:retry";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ObjectProvider<WebhookProcessingService> processingServiceProvider;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;

    public RedisRetryQueueService(StringRedisTemplate redis,
                                  ObjectMapper mapper,
                                  ObjectProvider<WebhookProcessingService> processingServiceProvider) {
        this.redis = redis;
        this.mapper = mapper;
        this.processingServiceProvider = processingServiceProvider;
    }

    public void pushRetry(String jsonPayload) {
        try {
            redis.opsForList().leftPush(RETRY_LIST, jsonPayload);
            log.info("Encolado retry persistente en Redis: {}", jsonPayload);
        } catch (Exception ex) {
            log.error("Fallo al encolar retry persistente en Redis: {}. Payload: {}", ex.toString(), jsonPayload);
            // En caso de fallo al encolar en Redis, podríamos aplicar un fallback (archivo local, DB, etc.)
        }
    }

    @PostConstruct
    public void startWorker() {
        executor.submit(this::runWorker);
    }

    @PreDestroy
    public void stopWorker() {
        running = false;
        executor.shutdownNow();
    }

    private void runWorker() {
        while (running) {
            try {
                // rightPop con timeout devuelve el elemento o null si timeout
                String entry = redis.opsForList().rightPop(RETRY_LIST, Duration.ofSeconds(5));
                if (entry != null) {
                    try {
                        log.info("Procesando retry desde Redis: {}", entry);
                        WebhookProcessingService processingService = processingServiceProvider.getIfAvailable();
                        if (processingService == null) {
                            log.warn("WebhookProcessingService no disponible, reencolando payload y esperando...");
                            // reencolar y esperar un poco
                            redis.opsForList().leftPush(RETRY_LIST, entry);
                            Thread.sleep(1000);
                            continue;
                        }
                        processingService.processRetryPayload(entry);
                    } catch (Exception ex) {
                        log.warn("Fallo al procesar retry payload, reencolando: {} - err: {}", entry, ex.toString());
                        // reencolar al frente para intentar más tarde
                        try {
                            redis.opsForList().leftPush(RETRY_LIST, entry);
                        } catch (Exception pushEx) {
                            log.error("No se pudo reencolar el payload tras fallo: {}", pushEx.toString());
                        }
                        // small sleep to avoid tight loop on persistent failure
                        Thread.sleep(1000);
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Error en worker RedisRetryQueueService: {}", e.toString());
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }
    }
}