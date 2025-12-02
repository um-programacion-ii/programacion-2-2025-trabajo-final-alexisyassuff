package com.cine.backend.controller;

import com.cine.backend.service.ReconciliationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/proxy/evento")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/{eventoId}/reconcile")
    public ResponseEntity<?> reconcile(@PathVariable String eventoId) {
        var report = reconciliationService.reconcile(eventoId);
        return ResponseEntity.ok(report);
    }
}