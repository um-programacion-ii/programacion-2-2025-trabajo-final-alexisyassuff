package com.cine.backend.controller;
import com.cine.backend.service.EventoSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/eventos")
public class EventoSyncController {

    private static final Logger log = LoggerFactory.getLogger(EventoSyncController.class);
    private final EventoSyncService syncService;

    public EventoSyncController(EventoSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync/all")
    public void syncAllEventosManual() {
        syncService.syncAllEventos();
    }

    @Scheduled(fixedRate = 3600000)
    public void periodicSyncAll() {
        syncService.syncAllEventos();
    }
}