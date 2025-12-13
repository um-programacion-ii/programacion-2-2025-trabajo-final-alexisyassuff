package com.cine.backend.controller;

import com.cine.backend.service.BackendProxyClient;
import com.cine.backend.service.WebhookProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/proxy")
public class BackendWebhookController {

    private static final Logger log = LoggerFactory.getLogger(BackendWebhookController.class);
    private final WebhookProcessingService processingService;
    private final BackendProxyClient proxyClient;

    public BackendWebhookController(WebhookProcessingService processingService,
                                    BackendProxyClient proxyClient) {
        this.processingService = processingService;
        this.proxyClient = proxyClient;
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> receiveWebhook(@RequestBody Map<String, Object> payload,
                                            @RequestHeader(value = "X-Webhook-Token", required = false) String token) {
        log.info("Webhook recibido: {}", payload);
        try {
            processingService.process(payload);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Error procesando webhook", ex);
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", "internal error"));
        }
    }

    @GetMapping("/evento/{id}/estado-combinado")
    public ResponseEntity<?> getCombined(@PathVariable("id") String eventoId) {
        Map<String, Object> combined = proxyClient.getCombinedEventoView(eventoId);
        return ResponseEntity.ok(combined);
    }
}