package com.cine.backend.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * HealthIndicator tolerante para la cátedra.
 * - Intenta hacer un GET al endpoint base (o /health si lo tiene).
 * - Si responde OK, devuelve UP con detalles.
 * - Si falla, devuelve UP con detail reachable=false + mensaje de error.
 *
 * Razón: evita que un servicio externo derribe el health global pero mantiene visibilidad.
 */
@Component
public class CatedraHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(CatedraHealthIndicator.class);

    private final RestTemplate rest;
    private final String healthUrl;

    public CatedraHealthIndicator(RestTemplateBuilder builder,
                                  @Value("${catedra.base-url}") String baseUrl,
                                  @Value("${catedra.health-path:/health}") String healthPath,
                                  @Value("${catedra.health-timeout-ms:1000}") long timeoutMs) {
        this.rest = builder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
        // Intentamos la URL de health típica, pero si no existe se puede adaptar
        this.healthUrl = baseUrl != null && !baseUrl.endsWith("/") ? baseUrl + healthPath : baseUrl + "/" + healthPath;
    }

    @Override
    public Health health() {
        try {
            ResponseEntity<String> resp = rest.getForEntity(healthUrl, String.class);
            boolean ok = resp.getStatusCode().is2xxSuccessful();
            return Health.up()
                    .withDetail("reachable", ok)
                    .withDetail("statusCode", resp.getStatusCodeValue())
                    .withDetail("url", healthUrl)
                    .build();
        } catch (Exception ex) {
            log.warn("Catedra health check failed: {}", ex.toString());
            // IMPORTANTE: devolvemos UP para no romper el estado global.
            // Conservamos detalles para que los operadores vean el fallo.
            return Health.up()
                    .withDetail("reachable", false)
                    .withDetail("error", ex.getMessage())
                    .withDetail("url", healthUrl)
                    .build();
        }
    }
}