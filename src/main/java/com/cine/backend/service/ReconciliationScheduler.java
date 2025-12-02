package com.cine.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler opcional para ejecuciones periódicas de reconciliación.
 * Para activarlo, asegúrate de que tu clase main/application tenga @EnableScheduling.
 *
 * Ajusta la expresión cron / fixedRate según tu necesidad.
 */
@Component
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);
    private final ReconciliationService reconciliationService;

    public ReconciliationScheduler(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    // ejemplo: cada 5 minutos
    @Scheduled(fixedRateString = "${reconciliation.fixedRateMs:300000}")
    public void scheduledReconcile() {
        String exampleEventoId = "1"; // podrías recorrer eventos configurados o leer un listado
        log.info("Scheduled reconcile trigger for eventoId={}", exampleEventoId);
        try {
            reconciliationService.reconcileAndMaybeApply(exampleEventoId, true, false); // dryRun by default
        } catch (Exception ex) {
            log.warn("Scheduled reconciliation failed: {}", ex.toString());
        }
    }
}