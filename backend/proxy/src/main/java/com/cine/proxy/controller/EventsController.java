package com.cine.proxy.controller;

import com.cine.proxy.client.CatedraClient;
import com.cine.proxy.client.CatedraException;
import com.cine.proxy.service.RedisSeatService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller implementing BOTH the enunciado endpoints AND frontend compatibility:
 * 
 * ENUNCIADO ENDPOINTS:
 * - GET /api/endpoints/v1/eventos
 * - GET /api/endpoints/v1/evento/{id}
 * - POST /api/endpoints/v1/bloquear-asientos
 * - POST /api/endpoints/v1/realizar-venta
 * - GET /api/endpoints/v1/listar-ventas
 * - GET /api/endpoints/v1/listar-venta/{id}
 * 
 * FRONTEND COMPATIBILITY ENDPOINTS:
 * - GET /eventos (maps to /api/endpoints/v1/eventos)
 * - GET /eventos/{id} (maps to /api/endpoints/v1/evento/{id})
 * - POST /eventos/{id}/bloquear (maps to /api/endpoints/v1/bloquear-asientos)
 * - POST /eventos/{id}/vender (maps to /api/endpoints/v1/realizar-venta)
 */
@RestController
public class EventsController {

    private static final Logger log = LoggerFactory.getLogger(EventsController.class);

    private final CatedraClient client;
    private final RedisSeatService seatService;
    private final ObjectMapper mapper = new ObjectMapper();

    public EventsController(CatedraClient client, RedisSeatService seatService) {
        this.client = client;
        this.seatService = seatService;
    }

    // ===== ENUNCIADO ENDPOINTS =====
    
    @GetMapping("/api/endpoints/v1/eventos")
    public ResponseEntity<?> listEventosEnunciado() {
        String body = client.listEventos();
        return ResponseEntity.ok().body(body);
    }

    @GetMapping("/api/endpoints/v1/evento/{id}")
    public ResponseEntity<?> getEventoEnunciado(@PathVariable String id) {
        try {
            // 1. Obtener datos del evento
            String eventoData = client.getEvento(id);
            
            // 2. Verificar si necesita inicializar asientos
            try {
                // Intentar obtener asientos para ver si ya están inicializados
                java.util.List<com.cine.proxy.model.Seat> seats = seatService.getSeatsForEvento(id);
                
                if (seats.isEmpty()) {
                    // No hay asientos - auto-inicializar
                    initializeSeatsForEvent(id, eventoData);
                    log.info("Auto-inicializados asientos para evento {}", id);
                }
            } catch (Exception seatEx) {
                // Error obteniendo asientos - intentar inicializar
                try {
                    initializeSeatsForEvent(id, eventoData);
                    log.info("Auto-inicializados asientos (después de error) para evento {}", id);
                } catch (Exception initEx) {
                    log.warn("No se pudieron auto-inicializar asientos para evento {}: {}", id, initEx.getMessage());
                }
            }
            
            return ResponseEntity.ok(eventoData);
            
        } catch (Exception ex) {
            log.error("Error obteniendo evento {}: {}", id, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"Error interno: " + ex.getMessage() + "\"}");
        }
    }
    
    /**
     * GET /api/endpoints/v1/evento/{id}/asientos - ENUNCIADO EXACTO - Get Event Seats
     */
    @GetMapping("/api/endpoints/v1/evento/{id}/asientos")
    public ResponseEntity<?> getAsientosEnunciado(@PathVariable String id) {
        try {
            // 1. Asegurar que el evento existe y tiene asientos inicializados
            try {
                String eventoData = client.getEvento(id);
                java.util.List<com.cine.proxy.model.Seat> seats = seatService.getSeatsForEvento(id);
                
                if (seats.isEmpty()) {
                    // Auto-inicializar si no existen
                    initializeSeatsForEvent(id, eventoData);
                    seats = seatService.getSeatsForEvento(id);
                }
            } catch (Exception ex) {
                log.error("Error inicializando asientos para evento {}: {}", id, ex.getMessage(), ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Error inicializando asientos: " + ex.getMessage() + "\"}");
            }
            
            // 2. Obtener todos los asientos del evento
            java.util.List<com.cine.proxy.model.Seat> seats = seatService.getSeatsForEvento(id);
            
            return ResponseEntity.ok(seats);
            
        } catch (Exception ex) {
            log.error("Error obteniendo asientos enunciado para evento {}: {}", id, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"Error interno: " + ex.getMessage() + "\"}");
        }
    }

    /**
     * Método auxiliar para inicializar asientos basándose en datos del evento
     * SOLO se ejecuta si la cátedra NO tiene asientos para este evento
     */
    private void initializeSeatsForEvent(String eventoId, String eventoData) throws Exception {
        com.fasterxml.jackson.databind.JsonNode eventNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(eventoData);
        
        int filas = eventNode.path("filaAsientos").asInt(0);
        int columnas = eventNode.path("columnAsientos").asInt(0);
        
        if (filas > 0 && columnas > 0) {
            log.info("Evento {} no tiene asientos en la cátedra. Inicializando matriz {}x{} = {} asientos", 
                    eventoId, filas, columnas, (filas * columnas));
                    
            // Generar matriz completa de asientos LIBRES basada en las dimensiones del evento
            for (int fila = 1; fila <= filas; fila++) {
                for (int columna = 1; columna <= columnas; columna++) {
                    String seatId = "r" + fila + "c" + columna;
                    com.cine.proxy.model.Seat seat = new com.cine.proxy.model.Seat(seatId, "LIBRE", null, java.time.Instant.now());
                    seatService.upsertSeatWithTimestamp(eventoId, seat);
                }
            }
            
            log.info("Inicializados {} asientos para evento {}", (filas * columnas), eventoId);
        } else {
            log.warn("Evento {} no tiene dimensiones válidas de asientos: filas={}, columnas={}", eventoId, filas, columnas);
        }
    }
    
    // ===== FRONTEND COMPATIBILITY ENDPOINTS =====
    
    @GetMapping("/eventos")
    public ResponseEntity<?> listEventosFrontend() {
        String body = client.listEventos();
        return ResponseEntity.ok().body(body);
    }

    @GetMapping("/eventos/{id}")
    public ResponseEntity<?> getEventoFrontend(@PathVariable String id) {
        String body = client.getEvento(id);
        return ResponseEntity.ok().body(body);
    }

    /**
     * GET /eventos/{id}/asientos - OBTENER ASIENTOS PARA EL FRONTEND
     * Devuelve la matriz completa de asientos para un evento
     */
    @GetMapping("/eventos/{id}/asientos")
    public ResponseEntity<?> getAsientosFrontend(@PathVariable String id) {
        try {
            // 1. Asegurar que el evento existe y tiene asientos inicializados
            try {
                String eventoData = client.getEvento(id);
                java.util.List<com.cine.proxy.model.Seat> seats = seatService.getSeatsForEvento(id);
                
                if (seats.isEmpty()) {
                    // Auto-inicializar si no existen
                    initializeSeatsForEvent(id, eventoData);
                    seats = seatService.getSeatsForEvento(id);
                }
            } catch (Exception ex) {
                log.error("Error inicializando asientos para evento {}: {}", id, ex.getMessage(), ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Error inicializando asientos: " + ex.getMessage() + "\"}");
            }
            
            // 2. Obtener todos los asientos del evento
            java.util.List<com.cine.proxy.model.Seat> seats = seatService.getSeatsForEvento(id);
            
            // 3. Convertir a formato JSON para el frontend
            Map<String, Object> response = new HashMap<>();
            response.put("eventoId", id);
            response.put("asientos", seats);
            response.put("totalAsientos", seats.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception ex) {
            log.error("Error obteniendo asientos para evento {}: {}", id, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"Error interno: " + ex.getMessage() + "\"}");
        }
    }

    /**
     * POST /eventos/{id}/bloquear - FRONTEND COMPATIBILITY
     * Converts individual frontend call to enunciado format
     */
    @PostMapping("/eventos/{id}/bloquear")
    public ResponseEntity<?> bloquearAsientoFrontend(@PathVariable("id") Integer id,
                                                     @RequestBody Map<String, Object> payload) {
        try {
            Map<String, Object> upstream = new HashMap<>(payload == null ? Map.of() : payload);
            upstream.put("eventoId", id);
            String json = mapper.writeValueAsString(upstream);

            String resp = client.executePost("/bloquear-asientos", json);
            return ResponseEntity.ok().body(resp);
        } catch (JsonProcessingException e) {
            log.error("Error serializando payload para bloqueo frontend: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid JSON payload");
        } catch (CatedraException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Error inesperado al bloquear asientos frontend: {}", e.getMessage(), e);
            return ResponseEntity.status(502).body("Error inesperado en el proxy");
        }
    }

    /**
     * POST /eventos/{id}/vender - FRONTEND COMPATIBILITY
     * Converts individual frontend call to enunciado format
     */
    @PostMapping("/eventos/{id}/vender")
    public ResponseEntity<?> venderAsientoFrontend(@PathVariable("id") Integer id,
                                                   @RequestBody Map<String, Object> payload) {
        try {
            Map<String, Object> upstream = new HashMap<>(payload == null ? Map.of() : payload);
            upstream.put("eventoId", id);
            String json = mapper.writeValueAsString(upstream);

            String resp = client.executePost("/realizar-venta", json);
            return ResponseEntity.ok().body(resp);
        } catch (JsonProcessingException e) {
            log.error("Error serializando payload para venta frontend: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid JSON payload");
        } catch (CatedraException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Error inesperado al realizar venta frontend: {}", e.getMessage(), e);
            return ResponseEntity.status(502).body("Error inesperado en el proxy");
        }
    }

    /**
     * POST /api/endpoints/v1/bloquear-asientos
     * Expected body: { "eventoId": <id>, "asientos": [ { "fila":.., "columna":.. }, ... ] }
     *
     * Forwards directly to cátedra backend with same endpoint.
     */
    @PostMapping("/api/endpoints/v1/bloquear-asientos")
    public ResponseEntity<?> bloquearAsientos(@RequestBody Map<String, Object> payload) {
        try {
            // Forward the payload as-is to cátedra backend
            String json = mapper.writeValueAsString(payload);

            // call upstream endpoint (path relative to base-url configured in CatedraProperties)
            String resp = client.executePost("/bloquear-asientos", json);
            return ResponseEntity.ok().body(resp);
        } catch (JsonProcessingException e) {
            log.error("Error serializando payload para bloqueo: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid JSON payload");
        } catch (CatedraException ex) {
            // rethrow to be handled by ControllerAdvice (keeps status & body mapping consistent)
            throw ex;
        } catch (Exception e) {
            log.error("Error inesperado al bloquear asientos: {}", e.getMessage(), e);
            return ResponseEntity.status(502).body("Error inesperado en el proxy");
        }
    }

    /**
     * POST /api/endpoints/v1/realizar-venta
     * Expected body: {
     *   "eventoId": <id>,
     *   "fecha": "...",
     *   "precioVenta": ...,
     *   "asientos": [ { "fila":.., "columna":.., "persona": "..." } , ... ]
     * }
     */
    @PostMapping("/api/endpoints/v1/realizar-venta")
    public ResponseEntity<?> realizarVenta(@RequestBody Map<String, Object> payload) {
        try {
            // Forward the payload as-is to cátedra backend
            String json = mapper.writeValueAsString(payload);

            String resp = client.executePost("/realizar-venta", json);
            return ResponseEntity.ok().body(resp);
        } catch (JsonProcessingException e) {
            log.error("Error serializando payload para venta: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid JSON payload");
        } catch (CatedraException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Error inesperado al realizar venta: {}", e.getMessage(), e);
            return ResponseEntity.status(502).body("Error inesperado en el proxy");
        }
    }

    /**
     * GET /api/endpoints/v1/listar-ventas
     * Returns list of all sales (successful and failed) for the student
     */
    @GetMapping("/api/endpoints/v1/listar-ventas")
    public ResponseEntity<?> listarVentas() {
        try {
            String resp = client.listarVentas();
            return ResponseEntity.ok().body(resp);
        } catch (CatedraException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Error inesperado al listar ventas: {}", e.getMessage(), e);
            return ResponseEntity.status(502).body("Error inesperado en el proxy");
        }
    }

    /**
     * GET /api/endpoints/v1/listar-venta/{id}
     * Returns detailed information about a specific sale by ID
     */
    @GetMapping("/api/endpoints/v1/listar-venta/{id}")
    public ResponseEntity<?> listarVenta(@PathVariable String id) {
        try {
            String resp = client.listarVenta(id);
            return ResponseEntity.ok().body(resp);
        } catch (CatedraException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Error inesperado al obtener venta {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(502).body("Error inesperado en el proxy");
        }
    }
}