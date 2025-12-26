package com.cine.backend.controller;

import com.cine.backend.model.Evento;
import com.cine.backend.repository.EventoRepository;
import com.cine.backend.service.EventoInitializationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@RestController
public class EventosController {

    private static final Logger log = LoggerFactory.getLogger(EventosController.class);

    private final EventoRepository eventoRepository;
    private final EventoInitializationService initializationService;

    public EventosController(EventoRepository eventoRepository,
                             EventoInitializationService initializationService) {
        this.eventoRepository = eventoRepository;
        this.initializationService = initializationService;
    }

    /**
     * Lista todos los eventos persistidos en la BD local.
     */
    @GetMapping("/api/endpoints/v1/eventos")
    public ResponseEntity<List<Evento>> getAllEventos() {
        try {
            List<Evento> eventos = eventoRepository.findAll();
            log.info("Consultando todos los eventos: {} encontrados", eventos.size());
            return ResponseEntity.ok(eventos);
        } catch (Exception e) {
            log.error("Error obteniendo todos los eventos: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @GetMapping("evento/{id}")
    public ResponseEntity<?> getEvento(@PathVariable("id") String eventoId) {
        try {
            Long id = Long.parseLong(eventoId);
            
            Optional<Evento> eventoOpt = eventoRepository.findById(id);
            
            if (eventoOpt.isPresent()) {
                Evento evento = eventoOpt.get();
                log.info("Evento {} encontrado en BD local", eventoId);
                return ResponseEntity.ok(eventoToMap(evento));
            }
            
            log.info("Evento {} no encontrado en BD local, intentando inicializar desde cátedra", eventoId);
            boolean initialized = initializationService.ensureEventoInitialized(eventoId);
            
            if (initialized) {
                // Intentar obtener nuevamente
                eventoOpt = eventoRepository.findById(id);
                if (eventoOpt.isPresent()) {
                    Evento evento = eventoOpt.get();
                    log.info("Evento {} inicializado y persistido correctamente", eventoId);
                    return ResponseEntity.ok(eventoToMap(evento));
                }
            }
            
            log.warn("No se pudo obtener o inicializar el evento {}", eventoId);
            Map<String, Object> error = new HashMap<>();
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            
        } catch (NumberFormatException e) {
            log.error("ID de evento inválido: {}", eventoId);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "ID inválido");
            error.put("eventoId", eventoId);
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error obteniendo evento {}: {}", eventoId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error interno");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }


    private Map<String, Object> eventoToMap(Evento evento) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", evento.getId());
        map.put("titulo", evento.getTitulo());
        map.put("descripcion", evento.getDescripcion());
        map.put("precio", evento.getPrecio());
        map.put("fecha", evento.getFecha());
        map.put("filas", evento.getFilas());
        map.put("columnas", evento.getColumnas());
        map.put("updatedAt", evento.getUpdatedAt());
        map.put("imagen", evento.getImagen());
        return map;
    }
}

