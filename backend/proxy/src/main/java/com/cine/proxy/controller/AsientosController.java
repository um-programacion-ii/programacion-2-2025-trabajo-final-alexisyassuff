package com.cine.proxy.controller;

import com.cine.proxy.model.Seat;
import com.cine.proxy.service.RedisSeatService;
import com.cine.proxy.service.SeatLockService;
import com.cine.proxy.client.CatedraClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/asientos")
public class AsientosController {
    private static final Logger log = LoggerFactory.getLogger(AsientosController.class);

    private final RedisSeatService seatService;
    private final SeatLockService seatLockService;
    private final CatedraClient catedraClient;

    public AsientosController(RedisSeatService seatService, SeatLockService seatLockService, CatedraClient catedraClient) {
        this.seatService = seatService;
        this.seatLockService = seatLockService;
        this.catedraClient = catedraClient;
    }

    /**
     * GET /asientos/{eventoId}
     * Devuelve lista de asientos enriquecida con:
     *  - status: LIBRE | BLOQUEADO | VENDIDO (según seatService + seatLockService)
     *  - holder: sessionId que bloqueó (solo si BLOQUEADO)
     *  - updatedAt: si lo provee RedisSeatService
     *
     * Retorna JSON array con objetos { seatId, status, holder, updatedAt }
     */
    @GetMapping("/{eventoId}")
    public ResponseEntity<List<Map<String,Object>>> getAsientos(@PathVariable String eventoId) {
        try {
            List<Seat> seats = seatService.getSeatsForEvento(eventoId);
            List<Map<String,Object>> out = new ArrayList<>();
            int eid;
            try {
                eid = Integer.parseInt(eventoId);
            } catch (Exception ex) {
                // fallback: si no es convertible, usamos -1 (seatLockService manejará)
                eid = -1;
            }

            for (Seat s : seats) {
                Map<String,Object> m = new HashMap<>();
                String seatId = s.getSeatId();
                m.put("seatId", seatId);

                boolean sold = seatLockService.isSold(eid, seatId);
                String owner = seatLockService.getLockOwner(eid, seatId);

                if (sold) {
                    m.put("status", "VENDIDO");
                } else if (owner != null) {
                    m.put("status", "BLOQUEADO");
                    m.put("holder", owner);
                } else {
                    // keep upstream status if provided, otherwise LIBRE
                    String upstreamStatus = s.getStatus();
                    m.put("status", upstreamStatus == null ? "LIBRE" : upstreamStatus);
                    // copy holder from upstream if present
                    if (s.getHolder() != null && !s.getHolder().isBlank()) {
                        m.put("holder", s.getHolder());
                    }
                }

                if (s.getUpdatedAt() != null) {
                    m.put("updatedAt", s.getUpdatedAt().toString());
                }

                out.add(m);
            }

            return ResponseEntity.ok(out);
        } catch (Exception ex) {
            log.error("Error assembling seats enriched list for evento {}: {}", eventoId, ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "internal");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of(err));
        }
    }

    /**
     * POST /asientos/{eventoId}/{seatId}/block
     * Header required: X-Session-Id
     */
    @PostMapping("/{eventoId}/{seatId}/block")
    public ResponseEntity<?> blockSeat(@PathVariable int eventoId,
                                       @PathVariable String seatId,
                                       @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        try {
            if (sessionId == null || sessionId.isBlank()) {
                Map<String,Object> resp = new HashMap<>();
                resp.put("error", "Missing X-Session-Id");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resp);
            }
            
            log.info("Intentando bloquear asiento {} evento {} con sessionId: {}", seatId, eventoId, sessionId);
            
            // PASO 1: Bloquear localmente (para demo inmediata)
            SeatLockService.BlockResult res = seatLockService.tryBlock(eventoId, seatId, sessionId);
            
            switch (res.type) {
                case SUCCESS:
                case ALREADY_LOCKED_BY_ME: {
                    // PASO 2: Actualizar Redis directamente para persistencia
                    try {
                        // Crear asiento BLOQUEADO y actualizarlo en Redis
                        Seat blockedSeat = new Seat(seatId, "BLOQUEADO", sessionId, java.time.Instant.now());
                        seatService.upsertSeatWithTimestamp(String.valueOf(eventoId), blockedSeat);
                        log.info("Asiento {} bloqueado y actualizado en Redis", seatId);
                        
                        // OPCIONAL: También enviar a cátedra si está configurada
                        Map<String, Object> filaColumna = parseSeatId(seatId);
                        if (filaColumna != null) {
                            Map<String, Object> payload = new HashMap<>();
                            payload.put("eventoId", eventoId);
                            payload.put("asientos", List.of(filaColumna));
                            
                            String jsonPayload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
                            String catedraResponse = catedraClient.executePost("/bloquear-asientos", jsonPayload);
                            log.info("También enviado a cátedra: {}", catedraResponse);
                        }
                    } catch (Exception ex) {
                        log.warn("Error actualizando Redis o enviando a cátedra: {}", ex.getMessage());
                        // NO fallar - el bloqueo local ya funcionó
                    }
                    
                    Map<String,Object> ok = new HashMap<>();
                    ok.put("result", "locked");
                    ok.put("owner", res.ownerSessionId);
                    return ResponseEntity.ok(ok);
                }
                case SOLD:
                    Map<String,Object> sold = new HashMap<>();
                    sold.put("error", "SOLD");
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(sold);
                case LOCKED_BY_OTHER:
                default:
                    Map<String,Object> conflict = new HashMap<>();
                    conflict.put("error", "LOCKED_BY_OTHER");
                    conflict.put("owner", res.ownerSessionId);
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(conflict);
            }
        } catch (Exception ex) {
            log.error("Error blockSeat: {}", ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "internal");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * POST /asientos/{eventoId}/{seatId}/unlock
     * Header: X-Session-Id
     */
    @PostMapping("/{eventoId}/{seatId}/unlock")
    public ResponseEntity<?> unlockSeat(@PathVariable int eventoId,
                                        @PathVariable String seatId,
                                        @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        try {
            if (sessionId == null || sessionId.isBlank()) {
                Map<String,Object> resp = new HashMap<>();
                resp.put("error", "Missing X-Session-Id");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resp);
            }
            
            log.info("Intentando desbloquear asiento {} evento {} con sessionId: {}", seatId, eventoId, sessionId);
            
            // Verificar también en Redis para asegurar consistencia
            boolean canUnlock = false;
            try {
                List<Seat> allSeats = seatService.getSeatsForEvento(String.valueOf(eventoId));
                Seat currentSeat = allSeats.stream()
                    .filter(s -> seatId.equals(s.getSeatId()))
                    .findFirst()
                    .orElse(null);
                
                if (currentSeat != null) {
                    log.info("Estado actual en Redis - seatId: {}, status: {}, holder: {}", 
                            currentSeat.getSeatId(), currentSeat.getStatus(), currentSeat.getHolder());
                    // Puede desbloquear si es el propietario o si está libre
                    canUnlock = sessionId.equals(currentSeat.getHolder()) || 
                               "LIBRE".equals(currentSeat.getStatus());
                } else {
                    // Si no existe en Redis, permitir desbloqueo (asiento libre por defecto)
                    canUnlock = true;
                    log.info("Asiento no encontrado en Redis, permitiendo desbloqueo");
                }
            } catch (Exception ex) {
                log.warn("Error verificando Redis, usando SeatLockService: {}", ex.getMessage());
                canUnlock = seatLockService.unlockIfOwner(eventoId, seatId, sessionId);
            }
            
            // También intentar desbloqueo en memoria
            boolean memoryUnlock = seatLockService.unlockIfOwner(eventoId, seatId, sessionId);
            log.info("Resultado desbloqueo - Redis: {}, Memoria: {}", canUnlock, memoryUnlock);
            
            if (canUnlock || memoryUnlock) {
                // También actualizar Redis para persistir el desbloqueo
                try {
                    Seat unlockedSeat = new Seat(seatId, "LIBRE", null, java.time.Instant.now());
                    seatService.upsertSeatWithTimestamp(String.valueOf(eventoId), unlockedSeat);
                    log.info("Asiento {} desbloqueado y actualizado en Redis", seatId);
                } catch (Exception ex) {
                    log.warn("Error actualizando Redis al desbloquear: {}", ex.getMessage());
                    // NO fallar - el desbloqueo local ya funcionó
                }
                
                Map<String,Object> r = new HashMap<>();
                r.put("result", "unlocked");
                return ResponseEntity.ok(r);
            } else {
                Map<String,Object> r = new HashMap<>();
                r.put("error", "NOT_OWNER_OR_NOT_LOCKED");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(r);
            }
        } catch (Exception ex) {
            log.error("Error unlockSeat: {}", ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "internal");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * POST /asientos/{eventoId}/initialize - INICIALIZAR MATRIZ DE ASIENTOS
     * Crea la matriz completa de asientos basándose en los datos del evento de la cátedra
     */
    @PostMapping("/{eventoId}/initialize")
    public ResponseEntity<?> initializeSeats(@PathVariable String eventoId) {
        try {
            // 1. Obtener datos del evento de la cátedra
            String eventData = catedraClient.getEvento(eventoId);
            com.fasterxml.jackson.databind.JsonNode eventNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(eventData);
            
            int filas = eventNode.path("filaAsientos").asInt(0);
            int columnas = eventNode.path("columnAsientos").asInt(0);
            
            if (filas == 0 || columnas == 0) {
                Map<String, Object> err = new HashMap<>();
                err.put("error", "Evento no tiene dimensiones válidas de asientos");
                return ResponseEntity.badRequest().body(err);
            }
            
            // 2. Generar matriz completa de asientos LIBRES
            List<Seat> seats = new ArrayList<>();
            for (int fila = 1; fila <= filas; fila++) {
                for (int columna = 1; columna <= columnas; columna++) {
                    String seatId = "r" + fila + "c" + columna;
                    Seat seat = new Seat(seatId, "LIBRE", null, java.time.Instant.now());
                    seats.add(seat);
                }
            }
            
            // 3. Guardar todos los asientos en Redis
            for (Seat seat : seats) {
                seatService.upsertSeatWithTimestamp(eventoId, seat);
            }
            
            log.info("Inicializados {} asientos para evento {} ({}x{})", seats.size(), eventoId, filas, columnas);
            
            Map<String, Object> response = new HashMap<>();
            response.put("result", "initialized");
            response.put("eventoId", eventoId);
            response.put("totalSeats", seats.size());
            response.put("dimensions", filas + "x" + columnas);
            return ResponseEntity.ok(response);
            
        } catch (Exception ex) {
            log.error("Error inicializando asientos para evento {}: {}", eventoId, ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Error inicializando: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * GET /asientos/{eventoId}/debug - DIAGNOSTICO DE ORIGEN DE DATOS
     * Te dice de dónde vienen los asientos para explicarle al profesor
     */
    @GetMapping("/{eventoId}/debug")
    public ResponseEntity<?> debugAsientos(@PathVariable String eventoId) {
        try {
            Map<String, Object> diagnostico = new HashMap<>();
            List<Seat> seats = seatService.getSeatsForEvento(eventoId);
            
            diagnostico.put("total_asientos", seats.size());
            diagnostico.put("origen_explicacion", "Datos vienen de Redis, que se populan via:");
            diagnostico.put("fuentes_posibles", List.of(
                "1. Kafka Consumer (AsientosConsumer) - eventos de cátedra",
                "2. Tests E2E ejecutados (BackendProxyE2ETest.java)",
                "3. Acciones de otros estudiantes en Redis compartido",
                "4. Tu propio bloqueo/compra desde la app"
            ));
            
            Map<String, Object> detalles = new HashMap<>();
            for (Seat s : seats) {
                Map<String, Object> info = new HashMap<>();
                info.put("status", s.getStatus());
                info.put("holder", s.getHolder());
                info.put("updatedAt", s.getUpdatedAt());
                
                // Analizar origen probable
                if ("aluX".equals(s.getHolder()) && s.getSeatId().equals("r2cX")) {
                    info.put("origen_probable", "Test E2E (BackendProxyE2ETest.java línea 95-98)");
                } else if (s.getHolder() != null && s.getHolder().startsWith("alu")) {
                    info.put("origen_probable", "Datos de prueba de cátedra o test");
                } else if (s.getHolder() != null && s.getHolder().contains("dev-token")) {
                    info.put("origen_probable", "Tu sesión desde la app Android");
                } else {
                    info.put("origen_probable", "Kafka o acción de otro estudiante");
                }
                
                detalles.put(s.getSeatId(), info);
            }
            diagnostico.put("asientos_detalle", detalles);
            
            return ResponseEntity.ok(diagnostico);
        } catch (Exception ex) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Error diagnosticando: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * POST /asientos/{eventoId}/{seatId}/free - ADMIN ENDPOINT FOR TESTING
     * Forces a seat to be FREE (libera cualquier asiento para testing)
     */
    @PostMapping("/{eventoId}/{seatId}/free")
    public ResponseEntity<?> freeSeat(@PathVariable int eventoId, @PathVariable String seatId) {
        try {
            // Forzar el asiento a LIBRE en Redis
            Seat freeSeat = new Seat(seatId, "LIBRE", null, java.time.Instant.now());
            seatService.upsertSeatWithTimestamp(String.valueOf(eventoId), freeSeat);
            
            // También liberar en memoria (forzado - cualquier session)
            seatLockService.unlockIfOwner(eventoId, seatId, "ADMIN-FORCE-UNLOCK");
            
            log.info("ADMIN: Asiento {} del evento {} forzado a LIBRE", seatId, eventoId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("result", "freed");
            response.put("seatId", seatId);
            response.put("eventoId", eventoId);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("Error liberando asiento {}: {}", seatId, ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "internal");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * POST /asientos/{eventoId}/{seatId}/purchase
     * Header: X-Session-Id
     */
    @PostMapping("/{eventoId}/{seatId}/purchase")
    public ResponseEntity<?> purchaseSeat(@PathVariable int eventoId,
                                          @PathVariable String seatId,
                                          @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        try {
            if (sessionId == null || sessionId.isBlank()) {
                Map<String,Object> resp = new HashMap<>();
                resp.put("error", "Missing X-Session-Id");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resp);
            }
            // PASO 1: Vender localmente (para demo inmediata)
            SeatLockService.PurchaseResult res = seatLockService.purchase(eventoId, seatId, sessionId);
            
            switch (res.type) {
                case SUCCESS: {
                    // PASO 2: Actualizar Redis directamente para persistencia
                    try {
                        // Crear asiento VENDIDO y actualizarlo en Redis
                        Seat soldSeat = new Seat(seatId, "VENDIDO", null, java.time.Instant.now());
                        seatService.upsertSeatWithTimestamp(String.valueOf(eventoId), soldSeat);
                        log.info("Asiento {} vendido y actualizado en Redis", seatId);
                        
                        // OPCIONAL: También enviar a cátedra si está configurada
                        Map<String, Object> filaColumna = parseSeatId(seatId);
                        if (filaColumna != null) {
                            Map<String, Object> payload = new HashMap<>();
                            payload.put("eventoId", eventoId);
                            payload.put("asientos", List.of(filaColumna));
                            payload.put("precioVenta", 1000.0);
                            
                            String jsonPayload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
                            String catedraResponse = catedraClient.executePost("/api/endpoints/v1/realizar-venta", jsonPayload);
                            log.info("También enviado venta a cátedra: {}", catedraResponse);
                        }
                    } catch (Exception ex) {
                        log.warn("Error actualizando Redis o enviando venta a cátedra: {}", ex.getMessage());
                        // NO fallar - la venta local ya funcionó
                    }
                    
                    Map<String,Object> ok = new HashMap<>();
                    ok.put("result", "purchased");
                    return ResponseEntity.ok(ok);
                }
                case SOLD: {
                    Map<String,Object> r = new HashMap<>();
                    r.put("error", "SOLD");
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(r);
                }
                case LOCKED_BY_OTHER: {
                    Map<String,Object> r = new HashMap<>();
                    r.put("error", "LOCKED_BY_OTHER");
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(r);
                }
                default: {
                    Map<String,Object> r = new HashMap<>();
                    r.put("error", "unknown");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(r);
                }
            }
        } catch (Exception ex) {
            log.error("Error purchaseSeat: {}", ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "internal");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * GET /asientos/{eventoId}/{seatId}/state
     * Optional header X-Session-Id to indicate "by me".
     */
    @GetMapping("/{eventoId}/{seatId}/state")
    public ResponseEntity<?> seatState(@PathVariable int eventoId,
                                       @PathVariable String seatId,
                                       @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        try {
            boolean sold = seatLockService.isSold(eventoId, seatId);
            String owner = seatLockService.getLockOwner(eventoId, seatId);
            String state;
            if (sold) state = "VENDIDO";
            else if (owner == null) state = "LIBRE";
            else if (sessionId != null && sessionId.equals(owner)) state = "BLOQUEADO_POR_MI";
            else state = "BLOQUEADO_POR_OTRO";

            Map<String, Object> resp = new HashMap<>();
            resp.put("state", state);
            if (owner != null) resp.put("owner", owner);
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            log.error("Error seatState: {}", ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "internal");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }
    
    /**
     * Convierte seatId formato "r2c10" a Map con fila/columna para cátedra
     * @param seatId formato "r2c10" 
     * @return Map con "fila":2, "columna":10 o null si no parseable
     */
    private Map<String, Object> parseSeatId(String seatId) {
        try {
            // Pattern para r{fila}c{columna}
            Pattern pattern = Pattern.compile("r(\\d+)c(\\d+)");
            Matcher matcher = pattern.matcher(seatId);
            
            if (matcher.matches()) {
                int fila = Integer.parseInt(matcher.group(1));
                int columna = Integer.parseInt(matcher.group(2));
                
                Map<String, Object> result = new HashMap<>();
                result.put("fila", fila);
                result.put("columna", columna);
                return result;
            }
            
            log.warn("No se pudo parsear seatId: {}", seatId);
            return null;
        } catch (Exception ex) {
            log.error("Error parseando seatId {}: {}", seatId, ex.getMessage());
            return null;
        }
    }
}