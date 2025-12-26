package com.cine.backend.service;
import com.cine.backend.model.Evento;
import com.cine.backend.repository.EventoRepository;
import com.cine.backend.service.EventoSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
public class EventoSyncService {

    private static final Logger log = LoggerFactory.getLogger(EventoSyncService.class);

    private final EventoRepository eventoRepository;
    private final RestTemplate restTemplate;

    public EventoSyncService(EventoRepository eventoRepository) {
        this.eventoRepository = eventoRepository;
        this.restTemplate = new RestTemplate();
    }

    public void syncAllEventos() {
        List<Long> ids = eventoRepository.findAll().stream()
                .map(Evento::getId)
                .collect(Collectors.toList());
        for (Long id : ids) {
            syncEvento(id);
        }
        log.info("[SYNC] Sincronización completa para {} eventos", ids.size());
    }

    public void syncEvento(Long externalId) {
        try {
            String url = "http://localhost:8081/eventos/" + externalId;
            Map eventData = restTemplate.getForObject(url, Map.class);
            if (eventData != null) {
                Evento evento = mapToEvento(eventData, externalId); 
                eventoRepository.save(evento);
            } else {
                log.warn("[SYNC] No se obtuvo evento {} desde el PROXY", externalId);
            }
        } catch (Exception ex) {
            log.error("[SYNC] Error sincronizando evento {}: {}", externalId, ex.getMessage());
        }
    }

    private Evento mapToEvento(Map data, Long id) {
        String titulo = (String) data.getOrDefault("titulo", "");
        String descripcion = (String) data.getOrDefault("descripcion", "");
        Double precio = data.containsKey("precioEntrada") ?
                Double.valueOf(data.get("precioEntrada").toString())
                : 0.0;
        String fechaStr = (String) data.get("fecha");
        LocalDateTime fecha = LocalDateTime.parse(fechaStr.replace("Z", ""));
        Integer filas = data.containsKey("filaAsientos") ?
                Integer.valueOf(data.get("filaAsientos").toString())
                : 0;
        Integer columnas = data.containsKey("columnaAsientos") ?
                Integer.valueOf(data.get("columnaAsientos").toString())
                : 0;
        String imagen = (String) data.getOrDefault("imagen", "");
        return new Evento(id, titulo, descripcion, precio, fecha, filas, columnas, imagen);
    }
}