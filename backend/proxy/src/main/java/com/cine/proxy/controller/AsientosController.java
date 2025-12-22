package com.cine.proxy.controller;
import com.cine.proxy.model.Seat;
import com.cine.proxy.service.RedisSeatService;
import com.cine.proxy.service.SeatLockService;
import com.cine.proxy.service.TokenService;
import com.cine.proxy.service.SessionTokenValidatorService;
import com.cine.proxy.client.CatedraClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
// @RequestMapping("/asientos")
public class AsientosController {
    private static final Logger log = LoggerFactory.getLogger(AsientosController.class);

    private final RedisSeatService seatService;
    private final SeatLockService seatLockService;
    private final CatedraClient catedraClient;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SessionTokenValidatorService sessionTokenValidatorService;

    public AsientosController(RedisSeatService seatService, SeatLockService seatLockService, CatedraClient catedraClient, StringRedisTemplate redis) {
        this.seatService = seatService;
        this.seatLockService = seatLockService;
        this.catedraClient = catedraClient;
        this.redis = redis;
    }



    @GetMapping("/asientos/{eventoId}")
    public ResponseEntity<List<Map<String,Object>>> getAsientos(
        @PathVariable String eventoId,
        @RequestHeader(value = "X-Session-Id", required = false) String sessionId)
    {
        try {
            log.info("Obteniendo asientos para evento {} - REFAC", eventoId);

            // 1. Obtener dimensiones (filas, columnas)
            int[] dims = getEventSeatDimensions(eventoId);
            int filas = dims[0], columnas = dims[1];
            log.info("Evento {}: {} filas x {} columnas", eventoId, filas, columnas);
            List<Map<String,Object>> allSeats = generateSeatMatrix(filas, columnas);

            // 2. Mergear estados de seats desde Redis
            mergeRedisSeatStates(allSeats, eventoId, sessionId);

            // 3. (Opcional) Mergear legacy sold/lock keys si falta info
            fallbackLegacyMerge(allSeats, eventoId, sessionId);

            // 4. Mergear memoria local si falta info de Redis
            mergeLocalSeatLocks(allSeats, eventoId, sessionId);

            log.info("Devolviendo {} asientos para evento {}", allSeats.size(), eventoId);
            return ResponseEntity.ok(allSeats);

        } catch (Exception ex) {
            log.error("Error generando matriz de asientos para evento {}: {}", eventoId, ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Error interno: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of(err));
        }
    }


    // 1. Devuelve [filas, columnas] del evento (JSON de cátedra)
    private int[] getEventSeatDimensions(String eventoId) {
        try {
            String eventoData = catedraClient.getEvento(eventoId);
            JsonNode eventoNode = eventoData != null ? objectMapper.readTree(eventoData) : null;
            int filas = eventoNode != null ? 
            int columnas = eventoNode != null ? 
            return new int[] { filas, columnas };
        } catch (Exception e) {
            log.warn("Error event dims {}: {}", eventoId, e.getMessage());
            return new int[] { 0, 0 };
        }
    }

    // 2. Genera matriz completa de asientos base en libre
    private List<Map<String,Object>> generateSeatMatrix(int filas, int columnas) {
        List<Map<String,Object>> res = new ArrayList<>();
        for (int fila = 1; fila <= filas; fila++) {
            for (int columna = 1; columna <= columnas; columna++) {
                String seatId = String.format("r%dc%d", fila, columna);
                Map<String,Object> seat = new HashMap<>();
                seat.put("seatId", seatId);
                seat.put("status", "LIBRE");
                seat.put("fila", fila);
                seat.put("columna", columna);
                res.add(seat);
            }
        }
        return res;
    }

    // 3. Merge info real de asientos desde Redis (BLOQUEADO/VENDIDO)
    private void mergeRedisSeatStates(List<Map<String,Object>> allSeats, String eventoId, String sessionId) {
        // Lógica igual que en tu for de antes: lee el evento de Redis, parsea el JSON asientos, actualiza cada asiento por su key asientoId/fila/columna
        // También comprobar expiración de bloqueo/vendido, holder, etc.
        // Usa la misma lógica de tu código original, pero reubicada aquí.
    }

    // 4. Si falta info, aplica fallback sold/lock keys de Redis ("sold:evento:seat", "seat_lock:...") (opcional)
    private void fallbackLegacyMerge(List<Map<String,Object>> allSeats, String eventoId, String sessionId) {
        // Lógica igual que tu fallback, para legacy keys: busca sold y seat_lock, actualiza status, holder si corresponde
    }

    // 5. Por si Redis/integraciones no traen la info, mergea estados en memoria local del lock service ("BLOQUEADO"/"VENDIDO")
    private void mergeLocalSeatLocks(List<Map<String,Object>> allSeats, String eventoId, String sessionId) {
        // Lógica igual: chequea por cada seat si está vendido/bloqueado en memoria local y completa el status.
    }



    // @GetMapping("/asientos/{eventoId}")
    // public ResponseEntity<List<Map<String,Object>>> getAsientos(@PathVariable String eventoId,
    //                                                             @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
    //     try {
    //         log.info("Obteniendo asientos para evento {} - NUEVA LÓGICA", eventoId);

    //         // 1. Obtener datos del evento desde cátedra para saber filas x columnas
    //         String eventoData = null;
    //         try {
    //             eventoData = catedraClient.getEvento(eventoId);
    //         } catch (Exception e) {
    //             log.warn("No se pudo obtener evento {} desde cátedra: {}", eventoId, e.getMessage());
    //             eventoData = null;
    //         }

    //         JsonNode eventoNode = null;
    //         int filas = 0;
    //         int columnas = 0;
    //         if (eventoData != null && !eventoData.isBlank()) {
    //             try {
    //                 log.info("JSON evento recibido (truncado): {}", eventoData.length() > 1000 ? eventoData.substring(0, 1000) + "..." : eventoData);
    //                 eventoNode = objectMapper.readTree(eventoData);
    //                 JsonNode evtNode = eventoNode;
    //                 if (evtNode.has("evento") && evtNode.path("evento").isObject()) {
    //                     evtNode = evtNode.path("evento");
    //                 }
    //                 final JsonNode searchNode = evtNode;
    //                 java.util.function.Function<String[], Integer> findInt = (String[] names) -> {
    //                     for (String name : names) {
    //                         JsonNode n = searchNode.path(name);
    //                         if (!n.isMissingNode() && !n.isNull()) {
    //                             if (n.isInt() || n.isLong()) {
    //                                 return n.asInt();
    //                             }
    //                             String txt = n.asText(null);
    //                             if (txt != null && !txt.isBlank()) {
    //                                 try {
    //                                     return Integer.parseInt(txt.trim());
    //                                 } catch (NumberFormatException ex) {
    //                                     try {
    //                                         double d = Double.parseDouble(txt.trim());
    //                                         return (int) d;
    //                                     } catch (Exception ex2) { /* ignore */ }
    //                                 }
    //                             }
    //                         }
    //                     }
    //                     return 0;
    //                 };

    //                 filas = findInt.apply(new String[] { "filaAsientos", "filas", "rows" });
    //                 columnas = findInt.apply(new String[] { "columnaAsientos", "columnAsientos", "columnas", "columns" });

    //             } catch (Exception e) {
    //                 log.warn("Error parseando JSON del evento {}: {}", eventoId, e.getMessage());
    //                 filas = 0;
    //                 columnas = 0;
    //             }
    //         } else {
    //             filas = 0;
    //             columnas = 0;
    //         }

    //         log.info("Evento {}: {} filas x {} columnas = {} asientos totales", eventoId, filas, columnas, filas * columnas);

    //         // 2. Generar matriz completa de asientos como LIBRE
    //         List<Map<String,Object>> allSeats = new ArrayList<>();
    //         for (int fila = 1; fila <= filas; fila++) {
    //             for (int columna = 1; columna <= columnas; columna++) {
    //                 String seatId = String.format("r%dc%d", fila, columna);
    //                 Map<String,Object> seat = new HashMap<>();
    //                 seat.put("seatId", seatId);
    //                 seat.put("status", "LIBRE");
    //                 seat.put("fila", fila);
    //                 seat.put("columna", columna);
    //                 allSeats.add(seat);
    //             }
    //         }
    //         log.info("Generada matriz base de {} asientos LIBRE", allSeats.size());

    //         // 3. Leer datos de Redis para mergear estados reales
    //         String redisKey = "evento_" + eventoId;
    //         log.info("=== DEBUG REDIS ===");
    //         log.info("Intentando leer key: '{}'", redisKey);
    //         log.info("Redis host: {}, port: {}, database: {}", "192.168.194.250", 6379, 0);

    //         try {
    //             Set<String> allKeys = redis.keys("*");
    //             log.info("Total keys visibles: {}", allKeys.size());
    //             log.info("Keys disponibles: {}", allKeys);
    //             Set<String> eventoKeys = redis.keys("evento_*");
    //             log.info("Keys evento_* encontradas: {}", eventoKeys);
    //         } catch (Exception e) {
    //             log.error("Error listando keys: {}", e.getMessage());
    //         }

    //         String redisData = null;
    //         try {
    //             redisData = redis.opsForValue().get(redisKey);
    //         } catch (Exception e) {
    //             log.error("Error leyendo key {} desde Redis: {}", redisKey, e.getMessage());
    //             redisData = null;
    //         }
    //         log.info("Resultado GET '{}': {}", redisKey, redisData != null ? "FOUND" : "NULL");

    //         long nowEpoch = java.time.Instant.now().getEpochSecond();

    //         if (redisData != null && !redisData.trim().isEmpty()) {
    //             try {
    //                 log.info("Datos encontrados en Redis para {}, mergeando...", redisKey);
    //                 log.info("Datos Redis (primeros 200 chars): {}", redisData.length() > 200 ? redisData.substring(0, 200) + "..." : redisData);

    //                 JsonNode redisRoot = objectMapper.readTree(redisData);
    //                 JsonNode redisSeats = redisRoot.path("asientos");

    //                 if (redisSeats.isArray()) {
    //                     int mergedCount = 0;

    //                     for (JsonNode redisSeat : redisSeats) {
    //                         int fila = redisSeat.path("fila").asInt(-1);
    //                         int columna = redisSeat.path("columna").asInt(-1);
    //                         String estado = redisSeat.path("estado").asText(null);

    //                         String status = "LIBRE";
    //                         if ("Vendido".equalsIgnoreCase(estado)) {
    //                             status = "VENDIDO";
    //                         } else if ("Bloqueado".equalsIgnoreCase(estado)) {
    //                             // comprobar expiración: preferir expiraEpoch si está
    //                             long expEpoch = 0;
    //                             JsonNode expEpochNode = redisSeat.path("expiraEpoch");
    //                             if (expEpochNode.isNumber()) {
    //                                 expEpoch = expEpochNode.asLong();
    //                             } else {
    //                                 String expIso = redisSeat.path("expira").asText(null);
    //                                 if (expIso != null) {
    //                                     try {
    //                                         expEpoch = java.time.OffsetDateTime.parse(expIso).toInstant().getEpochSecond();
    //                                     } catch (Exception ex) {
    //                                         expEpoch = 0;
    //                                     }
    //                                 }
    //                             }
    //                             if (expEpoch > nowEpoch) {
    //                                 status = "BLOQUEADO";
    //                             } else {
    //                                 // expirado -> tratar como LIBRE
    //                                 status = "LIBRE";
    //                             }
    //                         }

    //                         log.debug("Procesando asiento Redis - fila: {}, columna: {}, estado: {} -> {}", fila, columna, estado, status);

    //                         for (Map<String,Object> seat : allSeats) {
    //                             Integer seatFila = (Integer) seat.get("fila");
    //                             Integer seatColumna = (Integer) seat.get("columna");

    //                             if (seatFila != null && seatColumna != null && seatFila.equals(fila) && seatColumna.equals(columna)) {
    //                                 String prev = (String) seat.get("status");
    //                                 // no sobrescribir VENDIDO
    //                                 if (!"VENDIDO".equals(prev)) {
    //                                     seat.put("status", status);
    //                                     if (!"LIBRE".equals(status)) {
    //                                         seat.put("source", "redis");
    //                                         // set holder and mine only if BLOQUEADO
    //                                         if ("BLOQUEADO".equals(status)) {
    //                                             String holder = redisSeat.path("holder").asText(null);
    //                                             if (holder != null && !holder.isBlank()) {
    //                                                 seat.put("holder", holder);
    //                                                 seat.put("mine", sessionId != null && sessionId.equals(holder));
    //                                             } else {
    //                                                 seat.remove("holder");
    //                                                 seat.put("mine", false);
    //                                             }
    //                                         } else {
    //                                             // for VENDIDO, include buyer info if present (object -> map)
    //                                             JsonNode compradorNode = redisSeat.path("comprador");
    //                                             if (!compradorNode.isMissingNode() && !compradorNode.isNull() && compradorNode.isObject()) {
    //                                                 Map<String,Object> compradorMap = new HashMap<>();
    //                                                 compradorMap.put("persona", compradorNode.path("persona").asText(""));
    //                                                 compradorMap.put("fechaVenta", compradorNode.path("fechaVenta").asText(""));
    //                                                 // si hay otros campos dentro de comprador añadelos aquí
    //                                                 seat.put("comprador", compradorMap);
    //                                             } else {
    //                                                 // mantener compatibilidad: string vacío si no hay info
    //                                                 seat.put("comprador", "");
    //                                             }

    //                                         }
    //                                     }
    //                                 }
    //                                 mergedCount++;
    //                                 log.debug("Actualizado asiento fila {} columna {} a estado {}", fila, columna, status);
    //                                 break;
    //                             }
    //                         }
    //                     }

    //                     log.info("Mergeados {} asientos de Redis con estados específicos", mergedCount);
    //                 } else {
    //                     log.warn("Datos de Redis no contienen array 'asientos' válido para evento {}", eventoId);
    //                 }
    //             } catch (Exception e) {
    //                 log.error("Error parseando/mergeando datos JSON de Redis para {}: {}", eventoId, e.getMessage(), e);
    //             }
    //         } else {
    //             // FALLBACK: procesar sold y seat_lock keys (legacy), marcando holder/mine adecuadamente
    //             log.info("No hay datos JSON en Redis para evento {}. Aplicando fallback por claves sold/lock...", eventoId);
    //             try {
    //                 int mergedCount = 0;
    //                 // sold keys
    //                 Set<String> soldKeys = redis.keys("sold:" + eventoId + ":*");
    //                 if (soldKeys != null && !soldKeys.isEmpty()) {
    //                     log.info("Found sold keys for evento {}: {}", eventoId, soldKeys);
    //                     for (String soldKey : soldKeys) {
    //                         String[] parts = soldKey.split(":");
    //                         if (parts.length >= 3) {
    //                             String soldSeatId = parts[2];
    //                             java.util.regex.Matcher m = java.util.regex.Pattern.compile("r(\\d+)c(\\d+)").matcher(soldSeatId);
    //                             Integer sf = null, sc = null;
    //                             if (m.matches()) {
    //                                 sf = Integer.parseInt(m.group(1));
    //                                 sc = Integer.parseInt(m.group(2));
    //                             }
    //                             for (Map<String,Object> seat : allSeats) {
    //                                 Integer seatFila = (Integer) seat.get("fila");
    //                                 Integer seatColumna = (Integer) seat.get("columna");
    //                                 String seatIdStr = (String) seat.get("seatId");
    //                                 boolean match = false;
    //                                 if (sf != null && sc != null) {
    //                                     match = seatFila != null && seatColumna != null && seatFila.equals(sf) && seatColumna.equals(sc);
    //                                 } else {
    //                                     match = seatIdStr != null && seatIdStr.equals(soldSeatId);
    //                                 }
    //                                 if (match) {
    //                                     seat.put("status", "VENDIDO");
    //                                     seat.put("source", "redis-sold");
    //                                     mergedCount++;
    //                                     break;
    //                                 }
    //                             }
    //                         }
    //                     }
    //                 }

    //                 // lock keys fallback (legacy seat_lock keys) - treat as BLOQUEADO and include holder/mine
    //                 Set<String> lockKeys = redis.keys("seat_lock:" + eventoId + ":*");
    //                 if (lockKeys != null && !lockKeys.isEmpty()) {
    //                     log.info("Found lock keys for evento {}: {}", eventoId, lockKeys);
    //                     for (String lockKey : lockKeys) {
    //                         String[] parts = lockKey.split(":");
    //                         if (parts.length >= 3) {
    //                             String lockSeatId = parts[2];
    //                             String lockOwner = redis.opsForValue().get(lockKey);
    //                             java.util.regex.Matcher m = java.util.regex.Pattern.compile("r(\\d+)c(\\d+)").matcher(lockSeatId);
    //                             Integer lf = null, lc = null;
    //                             if (m.matches()) {
    //                                 lf = Integer.parseInt(m.group(1));
    //                                 lc = Integer.parseInt(m.group(2));
    //                             }
    //                             for (Map<String,Object> seat : allSeats) {
    //                                 Integer seatFila = (Integer) seat.get("fila");
    //                                 Integer seatColumna = (Integer) seat.get("columna");
    //                                 String seatIdStr = (String) seat.get("seatId");
    //                                 boolean match = false;
    //                                 if (lf != null && lc != null) {
    //                                     match = seatFila != null && seatColumna != null && seatFila.equals(lf) && seatColumna.equals(lc);
    //                                 } else {
    //                                     match = seatIdStr != null && seatIdStr.equals(lockSeatId);
    //                                 }
    //                                 if (match) {
    //                                     String current = (String) seat.get("status");
    //                                     if (!"VENDIDO".equals(current)) {
    //                                         seat.put("status", "BLOQUEADO");
    //                                         if (lockOwner != null && !lockOwner.isBlank()) {
    //                                             seat.put("holder", lockOwner);
    //                                             seat.put("mine", sessionId != null && sessionId.equals(lockOwner));
    //                                         }
    //                                         seat.put("source", "redis-lock");
    //                                         mergedCount++;
    //                                     }
    //                                     break;
    //                                 }
    //                             }
    //                         }
    //                     }
    //                 }

    //                 log.info("Fallback merge applied: {} seats marked from sold/locks for evento {}", mergedCount, eventoId);

    //             } catch (Exception e) {
    //                 log.error("Error applying fallback merge for evento {}: {}", eventoId, e.getMessage(), e);
    //                 log.info("No hay datos en Redis para evento {} - todos los asientos permanecen LIBRE", eventoId);
    //             }
    //         }

    //         // 4. También aplicar estados de memoria local (SeatLockService) para compatibilidad
    //         int eventoIdInt;
    //         try {
    //             eventoIdInt = Integer.parseInt(eventoId);
    //         } catch (Exception ex) {
    //             eventoIdInt = -1;
    //         }

    //         for (Map<String,Object> seat : allSeats) {
    //             String seatId = (String) seat.get("seatId");

    //             // Si no tiene estado específico de Redis, verificar memoria local
    //             String currentStatus = (String) seat.get("status");
    //             if ("LIBRE".equals(currentStatus)) {
    //                 boolean sold = seatLockService.isSold(eventoIdInt, seatId);
    //                 String owner = seatLockService.getLockOwner(eventoIdInt, seatId);

    //                 if (sold) {
    //                     seat.put("status", "VENDIDO");
    //                     if (owner != null) {
    //                         seat.put("holder", owner);
    //                         seat.put("mine", sessionId != null && sessionId.equals(owner));
    //                     }
    //                 } else if (owner != null) {
    //                     seat.put("status", "BLOQUEADO");
    //                     seat.put("holder", owner);
    //                     seat.put("mine", sessionId != null && sessionId.equals(owner));
    //                 }
    //             }
    //         }

    //         log.info("Devolviendo {} asientos para evento {}", allSeats.size(), eventoId);
    //         return ResponseEntity.ok(allSeats);

    //     } catch (Exception ex) {
    //         log.error("Error generando matriz de asientos para evento {}: {}", eventoId, ex.getMessage(), ex);
    //         Map<String, Object> err = new HashMap<>();
    //         err.put("error", "Error interno: " + ex.getMessage());
    //         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of(err));
    //     }
    // }



    @PostMapping("/api/endpoints/v1/bloquear-asientos")
    public ResponseEntity<?> bloquearAsientos(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
            System.out.println("[DEBUG] Token recibido en header: " + sessionId);
            if (sessionId == null || sessionId.isBlank() || !sessionTokenValidatorService.isSessionTokenValidRemoto(sessionId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid X-Session-Id"));
            }
        try {
            int eventoId = (int) request.get("eventoId"); // Obtener eventoId
            @SuppressWarnings("unchecked")
            List<String> seatIds = (List<String>) request.get("seatIds"); // Lista de asientos

            // Validar seatIds
            if (seatIds == null || seatIds.isEmpty() || seatIds.size() > 4) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("error", "Invalid seatIds (max 4 seats)");
                return ResponseEntity.badRequest().body(resp);
            }

            log.info("Intentando bloquear {} asientos para evento {} con sessionId: {}", 
                    seatIds.size(), eventoId, sessionId);

            List<String> blocked = new ArrayList<>();
            List<String> failed = new ArrayList<>();

            for (String seatId : seatIds) {
                try {
                    var result = seatLockService.tryBlock(eventoId, seatId, sessionId);
                    if (result.type == SeatLockService.BlockResultType.SUCCESS) {
                        blocked.add(seatId);
                        log.info("Asiento {} bloqueado exitosamente", seatId);
                    } else {
                        failed.add(seatId);
                        log.warn("No se pudo bloquear asiento {}: {}", seatId, result.type);
                    }
                } catch (Exception ex) {
                    failed.add(seatId);
                    log.error("Error bloqueando asiento {}: {}", seatId, ex.getMessage());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("blocked", blocked);
            response.put("failed", failed);
            response.put("totalBlocked", blocked.size());

            if (blocked.isEmpty()) {
                response.put("result", "none_blocked");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            } else if (failed.isEmpty()) {
                response.put("result", "all_blocked");
                return ResponseEntity.ok(response);
            } else {
                response.put("result", "partial_blocked");
                return ResponseEntity.ok(response);
            }

        } catch (Exception ex) {
            log.error("Error blockMultipleSeats: {}", ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "internal");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    @PostMapping("/bloquear-asiento")
    public ResponseEntity<?> bloquearAsiento(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
            System.out.println("[DEBUG] Token recibido en header: " + sessionId);
            if (sessionId == null || sessionId.isBlank() || !sessionTokenValidatorService.isSessionTokenValidRemoto(sessionId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid X-Session-Id"));
            }

        try {
            int eventoId = (int) request.get("eventoId");
            String seatId = (String) request.get("seatId");

            log.info("Intentando bloquear asiento {} para compra en evento {} con sessionId: {}", seatId, eventoId, sessionId);

            // Usar el sistema de bloqueo temporal para compra
            boolean blocked = seatService.tryBlockSeatForPurchase(String.valueOf(eventoId), seatId, sessionId);

            if (blocked) {
                Map<String, Object> ok = new HashMap<>();
                ok.put("result", "blocked_for_purchase");
                ok.put("seatId", seatId);
                ok.put("owner", sessionId);
                ok.put("ttl_minutes", 5);
                ok.put("message", "Asiento bloqueado. Tiene 5 minutos para completar la compra.");
                return ResponseEntity.ok(ok);
            } else {
                // Verificar por qué falló
                String lockOwner = seatService.getSeatLockOwner(String.valueOf(eventoId), seatId);
                if (lockOwner != null) {
                    Map<String, Object> conflict = new HashMap<>();
                    conflict.put("error", "BLOCKED_BY_OTHER");
                    conflict.put("owner", lockOwner);
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(conflict);
                } else {
                    Map<String, Object> unavailable = new HashMap<>();
                    unavailable.put("error", "SEAT_NOT_AVAILABLE");
                    unavailable.put("message", "Asiento no disponible para bloqueo (posiblemente vendido)");
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(unavailable);
                }
            }
        } catch (Exception ex) {
            log.error("Error blockSeatForPurchase: {}", ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "internal");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }



    // Endpoint de venta múltiple de asientos
    @PostMapping("/api/endpoints/v1/realizar-ventas")
    public ResponseEntity<?> venderAsientos(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        try {
            System.out.println("[DEBUG] Token recibido en header: " + sessionId);
            if (sessionId == null || sessionId.isBlank() || !sessionTokenValidatorService.isSessionTokenValidRemoto(sessionId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid X-Session-Id"));
            }

            // Extraer eventoId y seatIds del body
            int eventoId = (int) request.get("eventoId");
            @SuppressWarnings("unchecked")
            List<String> seatIds = (List<String>) request.get("seatIds");
            
            if (seatIds == null || seatIds.isEmpty() || seatIds.size() > 4) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("error", "Invalid seatIds (max 4 seats)");
                return ResponseEntity.badRequest().body(resp);
            }

            // Extraer datos del comprador
            String persona = (String) request.getOrDefault("persona", "Sin nombre");

            log.info("Intentando vender {} asientos para evento {} con sessionId: {} para persona: {}", 
                    seatIds.size(), eventoId, sessionId, persona);

            List<String> sold = new ArrayList<>();
            List<String> failed = new ArrayList<>();

            for (String seatId : seatIds) {
                try {
                    // Mantener la lógica existente
                    boolean purchased = seatService.tryPurchaseBlockedSeat(String.valueOf(eventoId), seatId, sessionId, persona, "");

                    if (purchased) {
                        seatLockService.purchase(eventoId, seatId, sessionId);
                        sold.add(seatId);
                    } else {
                        failed.add(seatId);
                        log.warn("No se pudo vender asiento {}: no bloqueado por usuario", seatId);
                    }
                } catch (Exception ex) {
                    failed.add(seatId);
                    log.error("Error vendiendo asiento {}: {}", seatId, ex.getMessage());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("sold", sold);
            response.put("failed", failed);
            response.put("totalSold", sold.size());

            if (sold.isEmpty()) {
                response.put("result", "none_sold");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            } else if (failed.isEmpty()) {
                response.put("result", "all_sold");
                return ResponseEntity.ok(response);
            } else {
                response.put("result", "partial_sold");
                return ResponseEntity.ok(response);
            }

        } catch (Exception ex) {
            log.error("Error purchaseMultipleSeats: {}", ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "internal");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    // Endpoint de venta individual de un asiento
    @PostMapping("/api/endpoints/v1/realizar-venta")
    public ResponseEntity<?> venderAsiento(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        try {
            System.out.println("[DEBUG] Token recibido en header: " + sessionId);
            if (sessionId == null || sessionId.isBlank() || !sessionTokenValidatorService.isSessionTokenValidRemoto(sessionId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid X-Session-Id"));
            }

            // Extraer eventoId y seatId del body
            int eventoId = (int) request.get("eventoId");
            String seatId = (String) request.get("seatId");

            // Extraer datos del comprador
            String persona = (String) request.getOrDefault("persona", "Sin nombre");

            log.info("Intentando comprar asiento {} evento {} con sessionId: {}", seatId, eventoId, sessionId);
            log.info("Datos del comprador - Persona: {}", persona);

            boolean purchased = seatService.tryPurchaseBlockedSeat(String.valueOf(eventoId), seatId, sessionId, persona, "");

            if (purchased) {
                seatLockService.purchase(eventoId, seatId, sessionId);

                try {
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
                    log.warn("Error enviando venta a cátedra: {}", ex.getMessage());
                    // NO fallar - la venta local ya funcionó
                }

                Map<String, Object> ok = new HashMap<>();
                ok.put("result", "purchased");
                ok.put("seatId", seatId);
                ok.put("owner", sessionId);
                ok.put("comprador", Map.of("persona", persona));
                ok.put("fechaVenta", java.time.Instant.now().toString());
                return ResponseEntity.ok(ok);

            } else {
                String lockOwner = seatService.getSeatLockOwner(String.valueOf(eventoId), seatId);

                if (lockOwner == null) {
                    Map<String, Object> r = new HashMap<>();
                    r.put("error", "SEAT_NOT_BLOCKED");
                    r.put("message", "Debe bloquear el asiento antes de comprarlo");
                    return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(r);
                } else if (!sessionId.equals(lockOwner)) {
                    Map<String, Object> r = new HashMap<>();
                    r.put("error", "BLOCKED_BY_OTHER");
                    r.put("owner", lockOwner);
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(r);
                } else {
                    Map<String, Object> r = new HashMap<>();
                    r.put("error", "SEAT_NOT_AVAILABLE");
                    r.put("message", "Asiento ya vendido o no disponible");
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(r);
                }
            }
        } catch (Exception ex) {
            log.error("Error purchaseSeat: {}", ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "internal");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }


    @PostMapping("/desbloquear-asiento")
    public ResponseEntity<?> desbloquearAsiento(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        try {
            System.out.println("[DEBUG] Token recibido en header: " + sessionId);
            if (sessionId == null || sessionId.isBlank() || !sessionTokenValidatorService.isSessionTokenValidRemoto(sessionId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid X-Session-Id"));
            }
            int eventoId = (int) request.get("eventoId");
            String seatId = (String) request.get("seatId");

            log.info("Intentando desbloquear asiento {} en evento {} con sessionId: {}", seatId, eventoId, sessionId);

            boolean released = seatService.releaseSeatLock(String.valueOf(eventoId), seatId, sessionId);

            if (released) {
                seatLockService.unlockIfOwner(eventoId, seatId, sessionId);
                Map<String, Object> r = new HashMap<>();
                r.put("result", "unlocked");
                return ResponseEntity.ok(r);
            } else {
                Map<String, Object> r = new HashMap<>();
                r.put("error", "NOT_OWNER_OR_NOT_LOCKED");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(r);
            }
        } catch (Exception ex) {
            log.error("Error desbloqueando asiento: {}", ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "internal");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    // Endpoint para desbloqueo de múltiples asientos
    @PostMapping("/desbloquear-asientos")
    public ResponseEntity<?> desbloquearAsientos(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        try {
            System.out.println("[DEBUG] Token recibido en header: " + sessionId);
            if (sessionId == null || sessionId.isBlank() || !sessionTokenValidatorService.isSessionTokenValidRemoto(sessionId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid X-Session-Id"));
            }

            int eventoId = (int) request.get("eventoId");
            @SuppressWarnings("unchecked")
            List<String> seatIds = (List<String>) request.get("seatIds");

            if (seatIds == null || seatIds.isEmpty() || seatIds.size() > 4) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("error", "Invalid seatIds (max 4 seats)");
                return ResponseEntity.badRequest().body(resp);
            }

            log.info("Intentando desbloquear múltiples asientos en evento {} con sessionId: {}", eventoId, sessionId);

            Map<String, Boolean> releaseResults = seatService.releaseMultipleSeatLocks(String.valueOf(eventoId), seatIds, sessionId);

            List<String> unlocked = new ArrayList<>();
            List<String> failed = new ArrayList<>();

            for (Map.Entry<String, Boolean> entry : releaseResults.entrySet()) {
                String seatId = entry.getKey();
                boolean released = entry.getValue();

                if (released) {
                    try {
                        seatLockService.unlockIfOwner(eventoId, seatId, sessionId);
                    } catch (Exception ex) {
                        log.warn("Error desbloqueando en memoria local para asiento {}: {}", seatId, ex.getMessage());
                    }
                    unlocked.add(seatId);
                } else {
                    failed.add(seatId);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("unlocked", unlocked);
            response.put("failed", failed);
            response.put("totalUnlocked", unlocked.size());

            if (unlocked.isEmpty()) {
                response.put("result", "none_unlocked");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            } else if (failed.isEmpty()) {
                response.put("result", "all_unlocked");
                return ResponseEntity.ok(response);
            } else {
                response.put("result", "partial_unlocked");
                return ResponseEntity.ok(response);
            }

        } catch (Exception ex) {
            log.error("Error desbloqueando múltiples asientos: {}", ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "internal");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }


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


    // GET /asientos/{eventoId}/{seatId}
    @GetMapping("/asientos/{eventoId}/{seatId}/state")
    public ResponseEntity<Map<String,Object>> getSeatState(@PathVariable String eventoId,
                                                        @PathVariable String seatId,
                                                        @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        try {
            // Reutilizamos la lógica existente: pedimos todos los asientos desde Redis y devolvemos el seat específico.
            List<Map<String,Object>> seats = getAsientos(eventoId, sessionId).getBody(); // careful: getAsientos returns ResponseEntity<List<Map...>>
            if (seats != null) {
                for (Map<String,Object> seat : seats) {
                    if (seatId.equals(seat.get("seatId"))) {
                        return ResponseEntity.ok(seat);
                    }
                }
            }
            // Si no está en la matriz (puede suceder), devolvemos 404
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "seat_not_found"));
        } catch (Exception ex) {
            log.error("Error getSeatState: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "internal"));
        }
    }
}