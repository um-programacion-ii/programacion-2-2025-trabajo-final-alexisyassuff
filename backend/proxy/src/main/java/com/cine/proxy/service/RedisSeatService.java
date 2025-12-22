package com.cine.proxy.service;

import com.cine.proxy.model.Seat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
/**
 * Servicio para leer/escribir asientos en Redis.
 * Añadimos upsertSeatWithTimestamp() para idempotencia/orden temporal.
 */
@Service
public class RedisSeatService {

    private static final Logger log = LoggerFactory.getLogger(RedisSeatService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisSeatService(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    private String keyForEvento(String eventoId) {
        return "evento_" + eventoId;
    }

    public List<Seat> getSeatsForEvento(String eventoId) {
        String key = keyForEvento(eventoId);
        log.info("Getting seats for evento {} with key {}", eventoId, key);
        
        log.info("Reading seats from Redis key: {}", key);

        // info: Verificar configuración de Redis
        try {
            log.info("Redis connection factory: {}", redis.getConnectionFactory().getClass().getSimpleName());
            log.info("Trying to read key {} from Redis...", key);
            
            // Verificar si la clave existe
            Boolean exists = redis.hasKey(key);
            log.info("Key {} exists: {}", key, exists);
            
            // Listar todas las claves que empiecen con evento
            Set<String> eventoKeys = redis.keys("evento*");
            log.info("All evento* keys in Redis: {}", eventoKeys);
            
        } catch (Exception infoEx) {
            log.error("Error in Redis info: {}", infoEx.getMessage());
        }

        // Primero intentar leer como JSON string (formato actual en Redis)
        String jsonStr = null;
        try {
            jsonStr = redis.opsForValue().get(key);
            log.info("Redis GET result for key {}: {}", key, jsonStr != null ? jsonStr.substring(0, Math.min(100, jsonStr.length())) + "..." : "null");
        } catch (Exception ex) {
            log.error("Error connecting to Redis: {}", ex.getMessage());
        }
        
        if (jsonStr != null && !jsonStr.isBlank()) {
            try {
                JsonNode root = mapper.readTree(jsonStr);
                List<Seat> seats = new ArrayList<>();
                
                log.info("Parsed JSON root node. Has asientos field: {}", root.has("asientos"));
                if (root.has("asientos")) {
                    JsonNode asientosNode = root.get("asientos");
                    log.info("Asientos node is array: {}, size: {}", asientosNode.isArray(), asientosNode.size());
                }
                
                if (root.has("asientos") && root.get("asientos").isArray()) {
                    JsonNode asientosArray = root.get("asientos");
                    log.info("Processing {} seats from Redis", asientosArray.size());
                    for (JsonNode asientoNode : asientosArray) {
                        int fila = asientoNode.get("fila").asInt();
                        int columna = asientoNode.get("columna").asInt();
                        String estado = asientoNode.get("estado").asText();
                        
                        // Crear seatId usando formato fila-columna
                        String seatId = fila + "-" + columna;
                        
                        // Convertir estado español a inglés
                        String status = estado.equals("Vendido") ? "VENDIDO" : 
                                       estado.equals("Libre") ? "LIBRE" : estado.toUpperCase();
                        
                        // Si tiene comprador, usar sus datos como holder
                        String holder = null;
                        if (asientoNode.has("comprador") && !asientoNode.get("comprador").isNull()) {
                            JsonNode compradorNode = asientoNode.get("comprador");
                            if (compradorNode.has("nombre") && compradorNode.has("apellido")) {
                                holder = compradorNode.get("nombre").asText() + " " + compradorNode.get("apellido").asText();
                            }
                        }
                        
                        Seat seat = new Seat(seatId, status, holder, Instant.now());
                        seats.add(seat);
                    }
                }
                return seats;
            } catch (Exception e) {
                log.error("Failed to parse JSON for evento {}: {}", eventoId, e.getMessage(), e);
                log.error("JSON that failed to parse: {}", jsonStr);
            }
        }
        
        // Fallback: intentar leer como hash (formato anterior)
        Map<Object, Object> entries = redis.opsForHash().entries(key);
        List<Seat> seats = new ArrayList<>();
        if (entries == null || entries.isEmpty()) return seats;

        entries.forEach((field, value) -> {
            try {
                String json = value.toString();
                Seat s = mapper.readValue(json, Seat.class);
                seats.add(s);
            } catch (Exception e) {
                // fallback: crear Seat mínimo usando constructor de 4 argumentos
                Seat fallback = new Seat(field.toString(), value.toString(), "", Instant.now());
                seats.add(fallback);
            }
        });
        return seats;
    }

    /**
     * Inserta o actualiza un asiento dentro del hash del evento.
     */
    public void upsertSeat(String eventoId, Seat seat) {
        try {
            String key = keyForEvento(eventoId);
            String json = mapper.writeValueAsString(seat);
            redis.opsForHash().put(key, seat.getSeatId(), json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upsert seat in Redis", e);
        }
    }

    /**
     * Upsert con lógica de idempotencia por timestamp:
     * - Si no existe: escribe el seat.
     * - Si existe: compara updatedAt y solo escribe si incoming.updatedAt >= existing.updatedAt.
     *
     * Implementación simple GET + PUT (suficiente para pruebas). Para alta concurrencia
     * puede implementarse en LUA para atomicidad.
     */
    // REEMPLAZA el método upsertSeatWithTimestamp por este

    // Reemplaza los métodos existentes por estas implementaciones robustas

    public void upsertSeatWithTimestamp(String eventoId, Seat incoming) {
        try {
            String key = keyForEvento(eventoId);
            String field = incoming.getSeatId();

            // zona local (GMT-3). Cambiar si necesitás otra zona.
            java.time.ZoneId zone = java.time.ZoneId.of("America/Argentina/Buenos_Aires");

            // ensure incoming has an updatedAt timestamp we can compare/use
            java.time.Instant nowInstant = java.time.Instant.now();
            if (incoming.getUpdatedAt() == null) {
                incoming.setUpdatedAt(nowInstant);
            }
            java.time.ZonedDateTime updatedZ = java.time.ZonedDateTime.ofInstant(incoming.getUpdatedAt(), zone);

            // read existing event JSON (string) -- we will update the "asientos" array in-place
            String eventJson = redis.opsForValue().get(key);
            com.fasterxml.jackson.databind.node.ObjectNode root;
            com.fasterxml.jackson.databind.node.ArrayNode arr;

            if (eventJson == null || eventJson.isBlank()) {
                // build a minimal event structure
                root = mapper.createObjectNode();
                try {
                    root.put("eventoId", Integer.parseInt(eventoId));
                } catch (NumberFormatException ignore) { /* no-op */ }
                arr = mapper.createArrayNode();
            } else {
                // parse existing event JSON
                com.fasterxml.jackson.databind.JsonNode parsed = mapper.readTree(eventJson);
                if (parsed.isObject()) {
                    root = (com.fasterxml.jackson.databind.node.ObjectNode) parsed;
                } else {
                    // if the stored value is not an object, replace with a fresh object
                    root = mapper.createObjectNode();
                    try {
                        root.put("eventoId", Integer.parseInt(eventoId));
                    } catch (NumberFormatException ignore) { /* no-op */ }
                }
                com.fasterxml.jackson.databind.JsonNode seatsNode = root.path("asientos");
                if (seatsNode.isArray()) {
                    arr = (com.fasterxml.jackson.databind.node.ArrayNode) seatsNode;
                } else {
                    arr = mapper.createArrayNode();
                }
            }

            // build the seat node that is compatible with both legacy and modern consumers
            com.fasterxml.jackson.databind.node.ObjectNode seatNode = mapper.createObjectNode();

            // Try to derive fila/columna from seatId (format "r<fila>c<col>")
            try {
                String s = field; // e.g. "r6c5"
                if (s != null && s.startsWith("r") && s.contains("c")) {
                    String[] parts = s.substring(1).split("c", 2);
                    int fila = Integer.parseInt(parts[0]);
                    int columna = Integer.parseInt(parts[1]);
                    seatNode.put("fila", fila);
                    seatNode.put("columna", columna);
                    // legacy fields left blank here; expira will be set only for BLOQUEADO below
                }
            } catch (Exception ex) {
                log.info("No se pudo derivar fila/columna desde seatId {}: {}", field, ex.getMessage());
            }

            // modern fields: always set updatedAt info
            seatNode.put("seatId", field);
            seatNode.put("updatedAt", updatedZ.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            seatNode.put("updatedAtEpoch", incoming.getUpdatedAt().getEpochSecond());
            if (incoming.getHolder() != null) {
                seatNode.put("holder", incoming.getHolder());
            } else {
                seatNode.put("holder", "");
            }
            if (incoming.getStatus() != null) {
                seatNode.put("status", incoming.getStatus());
            }

            // If incoming indicates a BLOQUEO, set legacy and expiration fields (5 minutes TTL from updatedAt)
            if (incoming.getStatus() != null && "BLOQUEADO".equalsIgnoreCase(incoming.getStatus())) {
                java.time.ZonedDateTime expZ = java.time.ZonedDateTime.ofInstant(incoming.getUpdatedAt(), zone).plusMinutes(5);
                seatNode.put("estado", "Bloqueado");
                seatNode.put("expira", expZ.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                seatNode.put("expiraEpoch", expZ.toInstant().getEpochSecond());
            }

            // find existing seat entry in arr by seatId or by fila/columna
            boolean replaced = false;
            for (int i = 0; i < arr.size(); i++) {
                com.fasterxml.jackson.databind.JsonNode existing = arr.get(i);
                com.fasterxml.jackson.databind.JsonNode sid = existing.path("seatId");
                String existingSeatId = null;
                if (sid != null && sid.isTextual()) existingSeatId = sid.asText();

                if (existingSeatId == null) {
                    // fallback by fila/columna
                    com.fasterxml.jackson.databind.JsonNode fNode = existing.path("fila");
                    com.fasterxml.jackson.databind.JsonNode cNode = existing.path("columna");
                    if (fNode.isInt() && cNode.isInt()) {
                        existingSeatId = "r" + fNode.asInt() + "c" + cNode.asInt();
                    }
                }

                if (existingSeatId != null && field.equals(existingSeatId)) {
                    // compare timestamps to avoid overwriting newer info
                    com.fasterxml.jackson.databind.JsonNode upd = existing.path("updatedAt");
                    boolean shouldReplace = false;
                    if (upd.isTextual()) {
                        try {
                            java.time.Instant existingTs = java.time.OffsetDateTime.parse(upd.asText()).toInstant();
                            if (!incoming.getUpdatedAt().isBefore(existingTs)) {
                                shouldReplace = true;
                            }
                        } catch (Exception ex) {
                            // if parse fails, be permissive and replace (avoid silent discard)
                            shouldReplace = true;
                        }
                    } else {
                        shouldReplace = true;
                    }

                    if (shouldReplace) {
                        // Merge: update only relevant fields, preserve comprador and other unrelated data
                        com.fasterxml.jackson.databind.node.ObjectNode existingNode = (com.fasterxml.jackson.databind.node.ObjectNode) existing;
                        com.fasterxml.jackson.databind.node.ObjectNode merged = existingNode.deepCopy();

                        // overwrite standard fields from seatNode if present
                        if (seatNode.has("status")) merged.set("status", seatNode.get("status"));
                        if (seatNode.has("estado")) merged.set("estado", seatNode.get("estado"));
                        if (seatNode.has("holder")) merged.set("holder", seatNode.get("holder"));
                        if (seatNode.has("updatedAt")) merged.set("updatedAt", seatNode.get("updatedAt"));
                        if (seatNode.has("updatedAtEpoch")) merged.set("updatedAtEpoch", seatNode.get("updatedAtEpoch"));
                        if (seatNode.has("expira")) merged.set("expira", seatNode.get("expira"));
                        if (seatNode.has("expiraEpoch")) merged.set("expiraEpoch", seatNode.get("expiraEpoch"));

                        // If incoming explicitly includes comprador (rare, e.g. from purchase), overwrite it
                        if (seatNode.has("comprador")) {
                            merged.set("comprador", seatNode.get("comprador"));
                            if (seatNode.has("fechaVenta")) merged.set("fechaVenta", seatNode.get("fechaVenta"));
                        }

                        // ensure fila/col/seatId are present
                        if (seatNode.has("fila")) merged.set("fila", seatNode.get("fila"));
                        if (seatNode.has("columna")) merged.set("columna", seatNode.get("columna"));
                        merged.put("seatId", field);

                        arr.set(i, merged);
                    } // else do not overwrite because existing is newer

                    replaced = true;
                    break;
                }
            }

            if (!replaced) {
                // add new node (no existing entry)
                arr.add(seatNode);
            }

            root.set("asientos", arr);

            // write back the whole event JSON (no new keys created)
            String newEventJson = mapper.writeValueAsString(root);
            redis.opsForValue().set(key, newEventJson);

        } catch (Exception e) {
            throw new RuntimeException("Failed to upsert seat with timestamp in Redis", e);
        }
    }

    public boolean tryPurchaseBlockedSeat(String eventoId, String seatId, String sessionId, String persona, String extraInfo) {
        try {
            String key = keyForEvento(eventoId);
            String eventJson = redis.opsForValue().get(key);
            if (eventJson == null || eventJson.isBlank()) {
                return false;
            }

            com.fasterxml.jackson.databind.node.ObjectNode root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(eventJson);
            com.fasterxml.jackson.databind.node.ArrayNode arr = root.withArray("asientos");

            long nowEpoch = java.time.Instant.now().getEpochSecond();

            for (int i = 0; i < arr.size(); i++) {
                com.fasterxml.jackson.databind.node.ObjectNode node = (com.fasterxml.jackson.databind.node.ObjectNode) arr.get(i);
                String sid = node.path("seatId").asText(null);
                if (sid == null || sid.isBlank()) {
                    com.fasterxml.jackson.databind.JsonNode fNode = node.path("fila");
                    com.fasterxml.jackson.databind.JsonNode cNode = node.path("columna");
                    if (fNode.isInt() && cNode.isInt()) {
                        sid = "r" + fNode.asInt() + "c" + cNode.asInt();
                    }
                }
                if (!seatId.equals(sid)) continue;

                String holder = node.path("holder").asText(null);
                long expEpoch = 0;
                com.fasterxml.jackson.databind.JsonNode expEpochNode = node.path("expiraEpoch");
                if (expEpochNode.isNumber()) expEpoch = expEpochNode.asLong();
                else {
                    String expIso = node.path("expira").asText(null);
                    if (expIso != null) {
                        try { expEpoch = java.time.OffsetDateTime.parse(expIso).toInstant().getEpochSecond(); }
                        catch (Exception ex) { expEpoch = 0; }
                    }
                }

                // must be locked by requester and not expired
                if (holder == null || !holder.equals(sessionId)) {
                    return false;
                }
                if (expEpoch != 0 && expEpoch <= nowEpoch) {
                    return false;
                }

                // OK: mark as VENDIDO and attach buyer info, but merge to preserve unrelated fields
                com.fasterxml.jackson.databind.node.ObjectNode merged = node.deepCopy();

                merged.put("status", "VENDIDO");
                merged.put("estado", "Vendido");

                com.fasterxml.jackson.databind.node.ObjectNode compradorNode = mapper.createObjectNode();
                compradorNode.put("persona", persona != null ? persona : "");
                String fechaVenta = java.time.ZonedDateTime.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"))
                        .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                compradorNode.put("fechaVenta", fechaVenta);
                merged.set("comprador", compradorNode);
                merged.put("fechaVenta", fechaVenta);

                // remove holder/expiration/updatedAt fields
                merged.remove("holder");
                merged.remove("expira");
                merged.remove("expiraEpoch");
                merged.remove("updatedAt");
                merged.remove("updatedAtEpoch");

                // persist merged node
                arr.set(i, merged);
                root.set("asientos", arr);
                String newEventJson = mapper.writeValueAsString(root);
                redis.opsForValue().set(key, newEventJson);

                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("tryPurchaseBlockedSeat error for {}:{} -> {}", eventoId, seatId, e.getMessage(), e);
            return false;
        }
    }

    // public boolean tryBlockSeatWithTTL(String eventoId, String seatId, String sessionId) {
    //     try {
    //         String key = keyForEvento(eventoId);
    //         // zona local (GMT-3)
    //         java.time.ZoneId zone = java.time.ZoneId.of("America/Argentina/Buenos_Aires");
    //         java.time.ZonedDateTime nowZ = java.time.ZonedDateTime.now(zone);
    //         java.time.ZonedDateTime expireZ = nowZ.plusMinutes(5); // TTL = 5 minutos
    //         java.time.Instant nowInstant = java.time.Instant.now();

    //         // Leer el JSON del evento (si no existe, construir estructura mínima)
    //         String eventJson = redis.opsForValue().get(key);
    //         com.fasterxml.jackson.databind.node.ObjectNode root;
    //         com.fasterxml.jackson.databind.node.ArrayNode arr;

    //         if (eventJson == null || eventJson.isBlank()) {
    //             root = mapper.createObjectNode();
    //             try { root.put("eventoId", Integer.parseInt(eventoId)); } catch (NumberFormatException ignore) {}
    //             arr = mapper.createArrayNode();
    //         } else {
    //             com.fasterxml.jackson.databind.JsonNode parsed = mapper.readTree(eventJson);
    //             if (parsed.isObject()) {
    //                 root = (com.fasterxml.jackson.databind.node.ObjectNode) parsed;
    //             } else {
    //                 root = mapper.createObjectNode();
    //                 try { root.put("eventoId", Integer.parseInt(eventoId)); } catch (NumberFormatException ignore) {}
    //             }
    //             com.fasterxml.jackson.databind.JsonNode seatsNode = root.path("asientos");
    //             if (seatsNode.isArray()) {
    //                 arr = (com.fasterxml.jackson.databind.node.ArrayNode) seatsNode;
    //             } else {
    //                 arr = mapper.createArrayNode();
    //             }
    //         }

    //         // Buscar índice del asiento por seatId o por fila/columna
    //         int foundIndex = -1;
    //         for (int i = 0; i < arr.size(); i++) {
    //             com.fasterxml.jackson.databind.JsonNode n = arr.get(i);
    //             com.fasterxml.jackson.databind.JsonNode sid = n.path("seatId");
    //             if (sid != null && sid.isTextual() && seatId.equals(sid.asText())) {
    //                 foundIndex = i;
    //                 break;
    //             }
    //             com.fasterxml.jackson.databind.JsonNode fNode = n.path("fila");
    //             com.fasterxml.jackson.databind.JsonNode cNode = n.path("columna");
    //             if (fNode.isInt() && cNode.isInt()) {
    //                 String cand = "r" + fNode.asInt() + "c" + cNode.asInt();
    //                 if (cand.equals(seatId)) {
    //                     foundIndex = i;
    //                     break;
    //                 }
    //             }
    //         }

    //         // Si existe asiento y está vendido -> no se puede bloquear
    //         if (foundIndex >= 0) {
    //             com.fasterxml.jackson.databind.JsonNode existing = arr.get(foundIndex);
    //             // comprobar estado vendido: 'status' o legacy 'estado'
    //             String statusTxt = existing.path("status").asText(null);
    //             String estadoTxt = existing.path("estado").asText(null);
    //             boolean sold = (statusTxt != null && "VENDIDO".equalsIgnoreCase(statusTxt))
    //                     || ("Vendido".equalsIgnoreCase(estadoTxt));
    //             if (sold) return false;

    //             // comprobar bloqueo vigente por otro (usar expiraEpoch si está presente)
    //             com.fasterxml.jackson.databind.JsonNode holderNode = existing.path("holder");
    //             com.fasterxml.jackson.databind.JsonNode expEpochNode = existing.path("expiraEpoch");
    //             com.fasterxml.jackson.databind.JsonNode expNode = existing.path("expira");

    //             if (expEpochNode.isNumber()) {
    //                 long expEpoch = expEpochNode.asLong();
    //                 if (expEpoch > nowInstant.getEpochSecond() && holderNode.isTextual() && !sessionId.equals(holderNode.asText())) {
    //                     return false;
    //                 }
    //             } else if (expNode.isTextual() && holderNode.isTextual()) {
    //                 try {
    //                     java.time.Instant exp = java.time.OffsetDateTime.parse(expNode.asText()).toInstant();
    //                     if (exp.isAfter(nowInstant) && !sessionId.equals(holderNode.asText())) {
    //                         return false;
    //                     }
    //                 } catch (Exception ex) {
    //                     if (holderNode.isTextual() && !sessionId.equals(holderNode.asText())) {
    //                         return false;
    //                     }
    //                 }
    //             } else if (holderNode.isTextual() && !sessionId.equals(holderNode.asText())) {
    //                 // sin campo expira pero con holder distinto -> considerar ocupado
    //                 return false;
    //             }
    //         }

    //         // Construir nodo actualizado (solo con campos necesarios)
    //         com.fasterxml.jackson.databind.node.ObjectNode updatedNode = mapper.createObjectNode();
    //         try {
    //             if (seatId != null && seatId.startsWith("r") && seatId.contains("c")) {
    //                 String[] parts = seatId.substring(1).split("c", 2);
    //                 int fila = Integer.parseInt(parts[0]);
    //                 int columna = Integer.parseInt(parts[1]);
    //                 updatedNode.put("fila", fila);
    //                 updatedNode.put("columna", columna);
    //                 updatedNode.put("estado", "Bloqueado"); // legacy field
    //                 updatedNode.put("expira", expireZ.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    //                 updatedNode.put("expiraEpoch", expireZ.toInstant().getEpochSecond()); // epoch seconds
    //             }
    //         } catch (Exception ex) {
    //             log.info("No se pudo derivar fila/columna desde seatId {}: {}", seatId, ex.getMessage());
    //         }

    //         updatedNode.put("seatId", seatId);
    //         updatedNode.put("status", "BLOQUEADO");
    //         updatedNode.put("holder", sessionId != null ? sessionId : "");
    //         updatedNode.put("updatedAt", nowZ.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    //         updatedNode.put("updatedAtEpoch", nowInstant.getEpochSecond());

    //         if (foundIndex >= 0) {
    //             // Merge with existing to avoid losing comprador u otros campos
    //             com.fasterxml.jackson.databind.node.ObjectNode existingNode = (com.fasterxml.jackson.databind.node.ObjectNode) arr.get(foundIndex);
    //             com.fasterxml.jackson.databind.node.ObjectNode merged = existingNode.deepCopy();

    //             // apply updatedNode fields
    //             if (updatedNode.has("estado")) merged.set("estado", updatedNode.get("estado"));
    //             if (updatedNode.has("expira")) merged.set("expira", updatedNode.get("expira"));
    //             if (updatedNode.has("expiraEpoch")) merged.set("expiraEpoch", updatedNode.get("expiraEpoch"));

    //             merged.put("seatId", seatId);
    //             merged.set("status", updatedNode.get("status"));
    //             merged.set("holder", updatedNode.get("holder"));
    //             merged.set("updatedAt", updatedNode.get("updatedAt"));
    //             merged.set("updatedAtEpoch", updatedNode.get("updatedAtEpoch"));

    //             // preserve comprador if present in existingNode (do not remove)
    //             if (existingNode.has("comprador") && !existingNode.get("comprador").isNull()) {
    //                 merged.set("comprador", existingNode.get("comprador"));
    //                 if (existingNode.has("fechaVenta")) merged.set("fechaVenta", existingNode.get("fechaVenta"));
    //             }

    //             arr.set(foundIndex, merged);
    //         } else {
    //             arr.add(updatedNode);
    //         }

    //         root.set("asientos", arr);

    //         // Escribir de vuelta el evento completo (NO crear claves nuevas)
    //         String newEventJson = mapper.writeValueAsString(root);
    //         redis.opsForValue().set(key, newEventJson);

    //         return true;

    //     } catch (Exception e) {
    //         log.error("Error bloqueando asiento {} en evento {}: {}", seatId, eventoId, e.getMessage(), e);
    //         return false;
    //     }
    // }

    public boolean tryBlockSeatWithTTL(String eventoId, String seatId, String sessionId) {
        try {
            String key = keyForEvento(eventoId); // Tipo: "evento_{eventId}"
            java.time.ZoneId zone = java.time.ZoneId.of("America/Argentina/Buenos_Aires");
            java.time.ZonedDateTime nowZ = java.time.ZonedDateTime.now(zone);
            java.time.ZonedDateTime expireZ = nowZ.plusMinutes(5);
            java.time.Instant nowInstant = java.time.Instant.now();

            log.info("[info][BLOCK] ---------- NUEVA OPERACION BLOQUEO ----------");
            log.info("[info][BLOCK] sessionId = {}", sessionId);
            log.info("[info][BLOCK] eventoId = {}, seatId = {}", eventoId, seatId);
            log.info("[info][BLOCK] Redis key a buscar: '{}'", key);

            String eventJson = redis.opsForValue().get(key);
            log.info("[info][BLOCK] JSON actual en Redis (key={}): {}", key, eventJson);

            com.fasterxml.jackson.databind.node.ObjectNode root;
            com.fasterxml.jackson.databind.node.ArrayNode arr;

            if (eventJson == null || eventJson.isBlank()) {
                // Solo si no existe, arma estructura (esto no crea clave en Redis aún, solo en memoria local)
                log.info("[info][BLOCK] NO existía evento en Redis: armo estructura mínima.");
                root = mapper.createObjectNode();
                try { root.put("eventoId", Integer.parseInt(eventoId)); } catch (NumberFormatException ignore) {}
                arr = mapper.createArrayNode();
            } else {
                log.info("[info][BLOCK] Evento encontrado en Redis, parseando...");
                com.fasterxml.jackson.databind.JsonNode parsed = mapper.readTree(eventJson);
                if (parsed.isObject()) {
                    root = (com.fasterxml.jackson.databind.node.ObjectNode) parsed;
                } else {
                    log.info("[info][BLOCK] Valor en Redis NO es objeto, reset structure.");
                    root = mapper.createObjectNode();
                    try { root.put("eventoId", Integer.parseInt(eventoId)); } catch (NumberFormatException ignore) {}
                }
                com.fasterxml.jackson.databind.JsonNode seatsNode = root.path("asientos");
                if (seatsNode.isArray()) {
                    arr = (com.fasterxml.jackson.databind.node.ArrayNode) seatsNode;
                } else {
                    arr = mapper.createArrayNode();
                }
            }

            log.info("[info][BLOCK] Asientos actuales (arr.size={}):", arr.size());
            for (int i = 0; i < arr.size(); i++) {
                log.info("[info][BLOCK] Asiento[{}]: {}", i, arr.get(i));
            }

            int foundIndex = -1;
            for (int i = 0; i < arr.size(); i++) {
                com.fasterxml.jackson.databind.JsonNode n = arr.get(i);
                com.fasterxml.jackson.databind.JsonNode sid = n.path("seatId");
                boolean match = false;
                if (sid != null && sid.isTextual() && seatId.equals(sid.asText())) {
                    foundIndex = i;
                    match = true;
                }
                if (!match) {
                    com.fasterxml.jackson.databind.JsonNode fNode = n.path("fila");
                    com.fasterxml.jackson.databind.JsonNode cNode = n.path("columna");
                    if (fNode.isInt() && cNode.isInt()) {
                        String cand = "r" + fNode.asInt() + "c" + cNode.asInt();
                        if (cand.equals(seatId)) {
                            foundIndex = i;
                        }
                    }
                }
                log.info("[info][BLOCK] Iteracion {}: seatId {}, foundIndex={}, match={}", i, sid, foundIndex, match);
            }

            if (foundIndex >= 0) {
                log.info("[info][BLOCK] Asiento YA EXISTE en evento (foundIndex={})", foundIndex);
                com.fasterxml.jackson.databind.JsonNode existing = arr.get(foundIndex);

                // Estado VENDIDO/legacy
                String statusTxt = existing.path("status").asText(null);
                String estadoTxt = existing.path("estado").asText(null);
                boolean sold = (statusTxt != null && "VENDIDO".equalsIgnoreCase(statusTxt))
                        || ("Vendido".equalsIgnoreCase(estadoTxt));
                log.info("[info][BLOCK] Estado seat: status={}, estado={}, sold={}", statusTxt, estadoTxt, sold);
                if (sold) {
                    log.info("[info][BLOCK] Asiento ya VENDIDO, no se puede bloquear. RETURN false");
                    return false;
                }

                // ¿Bloqueado por otro? (revisar expiración)
                com.fasterxml.jackson.databind.JsonNode holderNode = existing.path("holder");
                com.fasterxml.jackson.databind.JsonNode expEpochNode = existing.path("expiraEpoch");
                com.fasterxml.jackson.databind.JsonNode expNode = existing.path("expira");
                if (expEpochNode.isNumber()) {
                    long expEpoch = expEpochNode.asLong();
                    log.info("[info][BLOCK] expEpoch={}, ahora={}", expEpoch, nowInstant.getEpochSecond());
                    if (expEpoch > nowInstant.getEpochSecond() && holderNode.isTextual() && !sessionId.equals(holderNode.asText())) {
                        log.info("[info][BLOCK] Asiento BLOQUEADO por OTRO. RETURN false");
                        return false;
                    }
                } else if (expNode.isTextual() && holderNode.isTextual()) {
                    try {
                        java.time.Instant exp = java.time.OffsetDateTime.parse(expNode.asText()).toInstant();
                        log.info("[info][BLOCK] exp (iso)={}, ahora={}", exp, nowInstant);
                        if (exp.isAfter(nowInstant) && !sessionId.equals(holderNode.asText())) {
                            log.info("[info][BLOCK] Asiento BLOQUEADO por OTRO. RETURN false");
                            return false;
                        }
                    } catch (Exception ex) {
                        if (holderNode.isTextual() && !sessionId.equals(holderNode.asText())) {
                            log.info("[info][BLOCK] Error parseando expNode, pero holder es OTRO. RETURN false");
                            return false;
                        }
                    }
                } else if (holderNode.isTextual() && !sessionId.equals(holderNode.asText())) {
                    log.info("[info][BLOCK] Asiento BLOQUEADO por OTRO (sin expira explícito). RETURN false");
                    return false;
                }
            }

            log.info("[info][BLOCK] Asiento DISPONIBLE: se procederá a bloquearlo con holder={} por 5min", sessionId);

            // Nodo actualizado para el asiento
            com.fasterxml.jackson.databind.node.ObjectNode updatedNode = mapper.createObjectNode();
            try {
                if (seatId != null && seatId.startsWith("r") && seatId.contains("c")) {
                    String[] parts = seatId.substring(1).split("c", 2);
                    int fila = Integer.parseInt(parts[0]);
                    int columna = Integer.parseInt(parts[1]);
                    updatedNode.put("fila", fila);
                    updatedNode.put("columna", columna);
                    updatedNode.put("estado", "Bloqueado");
                    updatedNode.put("expira", expireZ.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                    updatedNode.put("expiraEpoch", expireZ.toInstant().getEpochSecond());
                }
            } catch (Exception ex) {
                log.info("No se pudo derivar fila/columna desde seatId {}: {}", seatId, ex.getMessage());
            }

            updatedNode.put("seatId", seatId);
            updatedNode.put("status", "BLOQUEADO");
            updatedNode.put("holder", sessionId != null ? sessionId : "");
            updatedNode.put("updatedAt", nowZ.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            updatedNode.put("updatedAtEpoch", nowInstant.getEpochSecond());

            // MERGE O CREAR
            if (foundIndex >= 0) {
                log.info("[info][BLOCK] Modo MERGE: actualizando asiento en arr[{}]", foundIndex);
                com.fasterxml.jackson.databind.node.ObjectNode existingNode = (com.fasterxml.jackson.databind.node.ObjectNode) arr.get(foundIndex);
                com.fasterxml.jackson.databind.node.ObjectNode merged = existingNode.deepCopy();
                // Mergeo sólo campos actualizables
                if (updatedNode.has("estado")) merged.set("estado", updatedNode.get("estado"));
                if (updatedNode.has("expira")) merged.set("expira", updatedNode.get("expira"));
                if (updatedNode.has("expiraEpoch")) merged.set("expiraEpoch", updatedNode.get("expiraEpoch"));
                merged.put("seatId", seatId);
                merged.set("status", updatedNode.get("status"));
                merged.set("holder", updatedNode.get("holder"));
                merged.set("updatedAt", updatedNode.get("updatedAt"));
                merged.set("updatedAtEpoch", updatedNode.get("updatedAtEpoch"));

                // Preserva info comprador
                if (existingNode.has("comprador") && !existingNode.get("comprador").isNull()) {
                    merged.set("comprador", existingNode.get("comprador"));
                    if (existingNode.has("fechaVenta")) merged.set("fechaVenta", existingNode.get("fechaVenta"));
                }

                arr.set(foundIndex, merged);
                log.info("[info][BLOCK] After MERGE nodo actualizado: {}", merged);
            } else {
                log.info("[info][BLOCK] Asiento NO EXISTÍA: agregando como NUEVO nodo");
                arr.add(updatedNode);
                log.info("[info][BLOCK] Nuevo nodo: {}", updatedNode);
            }

            root.set("asientos", arr);

            String newEventJson = mapper.writeValueAsString(root);
            log.info("[info][BLOCK] Evento final a persistir en Redis (key={}): {}", key, newEventJson);

            redis.opsForValue().set(key, newEventJson);

            log.info("[info][BLOCK] Operación EXITOSA: asiento {} bloqueado por session {} hasta {}", seatId, sessionId, expireZ);

            return true;

        } catch (Exception e) {
            log.error("Error bloqueando asiento {} en evento {}: {}", seatId, eventoId, e.getMessage(), e);
            return false;
        }
    }

    // Helper that reads the holder/owner for a seat from evento_<id> (returns null if none)
    public String getSeatHolder(String eventoId, String seatId) {
        try {
            String key = keyForEvento(eventoId);
            String eventJson = redis.opsForValue().get(key);
            if (eventJson == null || eventJson.isBlank()) return null;
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(eventJson);
            com.fasterxml.jackson.databind.JsonNode seats = root.path("asientos");
            if (!seats.isArray()) return null;
            for (com.fasterxml.jackson.databind.JsonNode sn : seats) {
                com.fasterxml.jackson.databind.JsonNode sid = sn.path("seatId");
                if (sid != null && sid.isTextual() && seatId.equals(sid.asText())) {
                    com.fasterxml.jackson.databind.JsonNode holder = sn.path("holder");
                    return holder.isTextual() ? holder.asText() : null;
                }
                // fallback by fila/col
                com.fasterxml.jackson.databind.JsonNode fNode = sn.path("fila");
                com.fasterxml.jackson.databind.JsonNode cNode = sn.path("columna");
                if (fNode.isInt() && cNode.isInt()) {
                    String cand = "r" + fNode.asInt() + "c" + cNode.asInt();
                    if (cand.equals(seatId)) {
                        com.fasterxml.jackson.databind.JsonNode holder = sn.path("holder");
                        return holder.isTextual() ? holder.asText() : null;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to read seat holder for {}:{} -> {}", eventoId, seatId, e.getMessage());
            return null;
        }
    }



    
    /**
     * Verifica si un asiento está bloqueado temporalmente.
     * 
     * @param eventoId ID del evento
     * @param seatId ID del asiento
     * @return sessionId del propietario del bloqueo, o null si no está bloqueado
     */
    public String getSeatLockOwner(String eventoId, String seatId) {
        try {
            String lockKey = lockKeyForSeat(eventoId, seatId);
            return redis.opsForValue().get(lockKey);
        } catch (Exception e) {
            log.error("Error verificando bloqueo de asiento {}: {}", seatId, e.getMessage());
            return null;
        }
    }

    private String lockKeyForSeat(String eventoId, String seatId) {
        // Elegí el formato que uses en el resto de tu código (por ejemplo:)
        return "seatlock:" + eventoId + ":" + seatId;
    }
    
    /**
     * Libera el bloqueo temporal de un asiento DEFINITIVAMENTE.
     * Solo funciona si el sessionId coincide con el propietario del bloqueo.
     * IMPORTANTE: Esto es para CANCELAR la reserva, no para preparar la venta.
     * 
     * @param eventoId ID del evento
     * @param seatId ID del asiento
     * @param sessionId ID de la sesión
     * @return true si se liberó exitosamente
     */
    // Release single seat lock by checking event JSON holder/expira
    public boolean releaseSeatLock(String eventoId, String seatId, String sessionId) {
        try {
            String key = keyForEvento(eventoId);
            // leer evento una sola vez
            String eventJson = redis.opsForValue().get(key);
            if (eventJson == null || eventJson.isBlank()) {
                log.warn("releaseSeatLock: event key {} not found", key);
                return false;
            }

            com.fasterxml.jackson.databind.node.ObjectNode root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(eventJson);
            com.fasterxml.jackson.databind.node.ArrayNode arr = root.withArray("asientos");

            boolean changed = false;
            long nowEpoch = java.time.Instant.now().getEpochSecond();

            for (int i = 0; i < arr.size(); i++) {
                com.fasterxml.jackson.databind.node.ObjectNode node = (com.fasterxml.jackson.databind.node.ObjectNode) arr.get(i);
                String sid = node.path("seatId").asText(null);
                // fallback by fila/columna if seatId absent
                if (sid == null || sid.isBlank()) {
                    com.fasterxml.jackson.databind.JsonNode fNode = node.path("fila");
                    com.fasterxml.jackson.databind.JsonNode cNode = node.path("columna");
                    if (fNode.isInt() && cNode.isInt()) {
                        sid = "r" + fNode.asInt() + "c" + cNode.asInt();
                    }
                }
                if (!seatId.equals(sid)) continue;

                String holder = node.path("holder").asText(null);
                long expEpoch = 0;
                com.fasterxml.jackson.databind.JsonNode expEpochNode = node.path("expiraEpoch");
                if (expEpochNode.isNumber()) {
                    expEpoch = expEpochNode.asLong();
                } else {
                    String expIso = node.path("expira").asText(null);
                    if (expIso != null) {
                        try {
                            expEpoch = java.time.OffsetDateTime.parse(expIso).toInstant().getEpochSecond();
                        } catch (Exception ex) { expEpoch = 0; }
                    }
                }

                // if no holder or holder!=session -> can't release
                if (holder == null || holder.isBlank() || !holder.equals(sessionId)) {
                    log.info("releaseSeatLock: holder mismatch or absent for {}:{} (holder={}, requested={})", eventoId, seatId, holder, sessionId);
                    return false;
                }

                // if expired already, treat as not owned
                if (expEpoch != 0 && expEpoch <= nowEpoch) {
                    log.info("releaseSeatLock: lock expired for {}:{} (expEpoch={})", eventoId, seatId, expEpoch);
                    return false;
                }

                // OK: release -> mark as LIBRE (legacy + modern fields)
                node.put("status", "LIBRE");
                node.put("estado", "Libre");
                node.remove("holder");
                node.remove("expira");
                node.remove("expiraEpoch");
                node.remove("updatedAt");
                node.remove("updatedAtEpoch");
                changed = true;
                break;
            }

            if (changed) {
                String newEventJson = mapper.writeValueAsString(root);
                redis.opsForValue().set(key, newEventJson);
                log.info("releaseSeatLock: released {}:{} by {}", eventoId, seatId, sessionId);
                return true;
            } else {
                log.info("releaseSeatLock: no change (seat not found or not owned) for {}:{}", eventoId, seatId);
                return false;
            }
        } catch (Exception e) {
            log.error("releaseSeatLock error for {}:{} -> {}", eventoId, seatId, e.getMessage(), e);
            return false;
        }
    }

    
    /**
     * Desbloquea múltiples asientos en una sola operación
     * @param eventoId ID del evento
     * @param seatIds Lista de IDs de asientos a desbloquear
     * @param sessionId ID de la sesión que debe ser propietaria
     * @return Map con el resultado por cada asiento: seatId -> true/false
     */

    // Release multiple seat locks in a single read/write (efficient)
    public Map<String, Boolean> releaseMultipleSeatLocks(String eventoId, List<String> seatIds, String sessionId) {
        Map<String, Boolean> results = new HashMap<>();
        try {
            String key = keyForEvento(eventoId);
            String eventJson = redis.opsForValue().get(key);
            if (eventJson == null || eventJson.isBlank()) {
                // none exist -> all false
                for (String s : seatIds) results.put(s, false);
                return results;
            }

            com.fasterxml.jackson.databind.node.ObjectNode root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(eventJson);
            com.fasterxml.jackson.databind.node.ArrayNode arr = root.withArray("asientos");

            long nowEpoch = java.time.Instant.now().getEpochSecond();

            // prepare a map for quick lookup from seatId->index
            Map<String,Integer> indexBySeat = new HashMap<>();
            for (int i = 0; i < arr.size(); i++) {
                com.fasterxml.jackson.databind.JsonNode node = arr.get(i);
                String sid = node.path("seatId").asText(null);
                if (sid == null || sid.isBlank()) {
                    com.fasterxml.jackson.databind.JsonNode fNode = node.path("fila");
                    com.fasterxml.jackson.databind.JsonNode cNode = node.path("columna");
                    if (fNode.isInt() && cNode.isInt()) {
                        sid = "r" + fNode.asInt() + "c" + cNode.asInt();
                    }
                }
                if (sid != null) indexBySeat.put(sid, i);
            }

            boolean anyChanged = false;
            for (String seatId : seatIds) {
                results.put(seatId, false); // default
                Integer idx = indexBySeat.get(seatId);
                if (idx == null) continue;
                com.fasterxml.jackson.databind.node.ObjectNode node = (com.fasterxml.jackson.databind.node.ObjectNode) arr.get(idx);

                String holder = node.path("holder").asText(null);
                long expEpoch = 0;
                com.fasterxml.jackson.databind.JsonNode expEpochNode = node.path("expiraEpoch");
                if (expEpochNode.isNumber()) expEpoch = expEpochNode.asLong();
                else {
                    String expIso = node.path("expira").asText(null);
                    if (expIso != null) {
                        try { expEpoch = java.time.OffsetDateTime.parse(expIso).toInstant().getEpochSecond(); }
                        catch (Exception ex) { expEpoch = 0; }
                    }
                }

                if (holder == null || holder.isBlank()) {
                    // not owned
                    continue;
                }
                if (!holder.equals(sessionId)) {
                    // owned by other
                    continue;
                }
                if (expEpoch != 0 && expEpoch <= nowEpoch) {
                    // expired
                    continue;
                }

                // release it
                node.put("status", "LIBRE");
                node.put("estado", "Libre");
                node.remove("holder");
                node.remove("expira");
                node.remove("expiraEpoch");
                node.remove("updatedAt");
                node.remove("updatedAtEpoch");

                anyChanged = true;
                results.put(seatId, true);
            }

            if (anyChanged) {
                String newEventJson = mapper.writeValueAsString(root);
                redis.opsForValue().set(key, newEventJson);
            }

            return results;
        } catch (Exception e) {
            log.error("releaseMultipleSeatLocks error for evento {}: {}", eventoId, e.getMessage(), e);
            for (String s : seatIds) results.put(s, false);
            return results;
        }
    }


    public boolean tryBlockSeatForPurchase(String eventoId, String seatId, String sessionId) {
        // Reutilizar la lógica existente de bloqueo temporal
        return tryBlockSeatWithTTL(eventoId, seatId, sessionId);
    }
    
    /**
     * Versión de compatibilidad para compra sin datos del comprador
     */
    public boolean tryPurchaseBlockedSeat(String eventoId, String seatId, String sessionId) {
        return tryPurchaseBlockedSeat(eventoId, seatId, sessionId, "Sin nombre", "");
    }
}
