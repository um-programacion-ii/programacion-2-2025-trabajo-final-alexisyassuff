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

    /**
     * POST /internal/proxy/evento/{eventoId}/reconcile
     * Query params:
     *  - dryRun=true  -> only returns report, no changes applied
     *  - apply=true   -> apply detected changes according to policy
     */
    @PostMapping("/{eventoId}/reconcile")
    public ResponseEntity<?> reconcile(
            @PathVariable String eventoId,
            @RequestParam(name = "dryRun", required = false, defaultValue = "false") boolean dryRun,
            @RequestParam(name = "apply", required = false, defaultValue = "false") boolean apply) {

        var report = reconciliationService.reconcileAndMaybeApply(eventoId, dryRun, apply);
        return ResponseEntity.ok(report);
    }
}