package com.cine.proxy.controller;

import com.cine.proxy.service.MetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalMetricsController {

    private final MetricsService metrics;

    public InternalMetricsController(MetricsService metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/internal/metrics")
    public ResponseEntity<?> metrics() {
        return ResponseEntity.ok(
                java.util.Map.of(
                        "eventsReceived", metrics.getEventsReceived(),
                        "eventsProcessed", metrics.getEventsProcessed()
                )
        );
    }
}
