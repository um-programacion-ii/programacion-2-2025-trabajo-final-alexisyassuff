package com.cine.proxy.webhook;

import com.cine.proxy.model.Seat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Envía notificaciones al Backend (webhook) de forma no bloqueante.
 * - URL configurable via backend.webhook.url
 * - No lanza excepciones en caso de fallo; loguea y hace fire-and-forget.
 */
@Component
public class WebhookNotifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotifier.class);

    private final WebClient webClient;
    private final String webhookUrl;
    private final long timeoutMs;

    public WebhookNotifier(WebClient.Builder webClientBuilder,
                           @Value("${backend.webhook.url:}") String webhookUrl,
                           @Value("${backend.webhook.timeout-ms:2000}") long timeoutMs) {
        this.webhookUrl = webhookUrl;
        this.timeoutMs = timeoutMs;
        this.webClient = webClientBuilder.build();
    }

    public void notifySeatChange(String eventoId, Seat seat) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("Webhook URL no configurada, omitiendo notificación para eventoId={} seat={}", eventoId, seat.getSeatId());
            return;
        }

        Map<String, Object> payload = Map.of(
                "eventoId", eventoId,
                "seatId", seat.getSeatId(),
                "status", seat.getStatus(),
                "holder", seat.getHolder(),
                "updatedAt", seat.getUpdatedAt() == null ? null : seat.getUpdatedAt().toString()
        );

        webClient.post()
                .uri(webhookUrl)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(Mono.just(payload), Map.class)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .doOnNext(resp -> log.debug("Webhook notificado OK: eventoId={} seat={} resp={}", eventoId, seat.getSeatId(), resp))
                .doOnError(err -> log.warn("Fallo notificando webhook para eventoId={} seat={}: {}", eventoId, seat.getSeatId(), err.toString()))
                .onErrorResume(e -> Mono.empty())
                .subscribe(); // fire-and-forget
    }
}