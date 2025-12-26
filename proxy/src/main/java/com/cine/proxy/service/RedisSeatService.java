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

@Service
public class RedisSeatService {

    private static final Logger log = LoggerFactory.getLogger(RedisSeatService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisSeatService(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }


   
    public void upsertSeatWithTimestamp(String eventoId, Seat incoming) {
        try {
            String key = keyForEvento(eventoId);
            String field = incoming.getSeatId();

            java.time.ZoneId zone = java.time.ZoneId.of("America/Argentina/Buenos_Aires");

            java.time.Instant nowInstant = java.time.Instant.now();
            if (incoming.getUpdatedAt() == null) {
                incoming.setUpdatedAt(nowInstant);
            }
            java.time.ZonedDateTime updatedZ = java.time.ZonedDateTime.ofInstant(incoming.getUpdatedAt(), zone);

            String eventJson = redis.opsForValue().get(key);
            com.fasterxml.jackson.databind.node.ObjectNode root;
            com.fasterxml.jackson.databind.node.ArrayNode arr;

            if (eventJson == null || eventJson.isBlank()) {
                root = mapper.createObjectNode();
                try {
                    root.put("eventoId", Integer.parseInt(eventoId));
                } catch (NumberFormatException ignore) { /* no-op */ }
                arr = mapper.createArrayNode();
            } else {
               
                // Existe --> Parseás el contenido, y extraés la estructura actual:
                com.fasterxml.jackson.databind.JsonNode parsed = mapper.readTree(eventJson);
                if (parsed.isObject()) {
                    root = (com.fasterxml.jackson.databind.node.ObjectNode) parsed;
                } else {
                    root = mapper.createObjectNode();
                    try {
                        root.put("eventoId", Integer.parseInt(eventoId));
                    } catch (NumberFormatException ignore) { /* no-op */ }
                }
                // Si existe array actual de asientos
                com.fasterxml.jackson.databind.JsonNode seatsNode = root.path("asientos");
                if (seatsNode.isArray()) {
                    arr = (com.fasterxml.jackson.databind.node.ArrayNode) seatsNode;
                } else {
                    arr = mapper.createArrayNode();
                }
            }

        //Armar asiento nuevo o actualizado completando datos
            com.fasterxml.jackson.databind.node.ObjectNode seatNode = mapper.createObjectNode();

            try {
                String s = field; 
                if (s != null && s.startsWith("r") && s.contains("c")) {
                    String[] parts = s.substring(1).split("c", 2);
                    int fila = Integer.parseInt(parts[0]);
                    int columna = Integer.parseInt(parts[1]);
                    seatNode.put("fila", fila);
                    seatNode.put("columna", columna);
                }
            } catch (Exception ex) {
                log.info("No se pudo derivar fila/columna desde seatId {}: {}", field, ex.getMessage());
            }

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

            if (incoming.getStatus() != null && "BLOQUEADO".equalsIgnoreCase(incoming.getStatus())) {
                java.time.ZonedDateTime expZ = java.time.ZonedDateTime.ofInstant(incoming.getUpdatedAt(), zone).plusMinutes(5);
                seatNode.put("estado", "Bloqueado");
                seatNode.put("expira", expZ.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                seatNode.put("expiraEpoch", expZ.toInstant().getEpochSecond());
            }

            boolean replaced = false;
            for (int i = 0; i < arr.size(); i++) {
                com.fasterxml.jackson.databind.JsonNode existing = arr.get(i);
                com.fasterxml.jackson.databind.JsonNode sid = existing.path("seatId");
                String existingSeatId = null;
                if (sid != null && sid.isTextual()) existingSeatId = sid.asText();

                if (existingSeatId == null) {
                    com.fasterxml.jackson.databind.JsonNode fNode = existing.path("fila");
                    com.fasterxml.jackson.databind.JsonNode cNode = existing.path("columna");
                    if (fNode.isInt() && cNode.isInt()) {
                        existingSeatId = "r" + fNode.asInt() + "c" + cNode.asInt();
                    }
                }
                // Busca el timestamp existente:
                if (existingSeatId != null && field.equals(existingSeatId)) {
                    com.fasterxml.jackson.databind.JsonNode upd = existing.path("updatedAt");
                    boolean shouldReplace = false;
                    if (upd.isTextual()) {
                        try {
                            java.time.Instant existingTs = java.time.OffsetDateTime.parse(upd.asText()).toInstant();
                            // Solo actualiza si el update entrante NO es más viejo que el guardado
                            if (!incoming.getUpdatedAt().isBefore(existingTs)) {
                                shouldReplace = true;
                            }
                        } catch (Exception ex) {
                            shouldReplace = true;
                        }
                    } else {
                        shouldReplace = true;
                    }

                    if (shouldReplace) {
                        com.fasterxml.jackson.databind.node.ObjectNode existingNode = (com.fasterxml.jackson.databind.node.ObjectNode) existing;
                        com.fasterxml.jackson.databind.node.ObjectNode merged = existingNode.deepCopy();
                        
                        // Actualiza SOLO los campos de ese asiento usando los nuevos valores:
                        if (seatNode.has("status")) merged.set("status", seatNode.get("status"));
                        if (seatNode.has("estado")) merged.set("estado", seatNode.get("estado"));
                        if (seatNode.has("holder")) merged.set("holder", seatNode.get("holder"));
                        if (seatNode.has("updatedAt")) merged.set("updatedAt", seatNode.get("updatedAt"));
                        if (seatNode.has("updatedAtEpoch")) merged.set("updatedAtEpoch", seatNode.get("updatedAtEpoch"));
                        if (seatNode.has("expira")) merged.set("expira", seatNode.get("expira"));
                        if (seatNode.has("expiraEpoch")) merged.set("expiraEpoch", seatNode.get("expiraEpoch"));

                        if (seatNode.has("comprador")) {
                            merged.set("comprador", seatNode.get("comprador"));
                            if (seatNode.has("fechaVenta")) merged.set("fechaVenta", seatNode.get("fechaVenta"));
                        }

                        if (seatNode.has("fila")) merged.set("fila", seatNode.get("fila"));
                        if (seatNode.has("columna")) merged.set("columna", seatNode.get("columna"));
                        merged.put("seatId", field);
                        
                        // Solo se reemplaza ese asiento en el array de asientos
                        arr.set(i, merged);
                    } 
                    // Si guardado es el más reciente, deja el existente tal como está
                    replaced = true;
                    break;
                }
            }

            // Si es un asiento nuevo (no estaba en el array), lo agrega
            if (!replaced) {
                arr.add(seatNode);
            }

            root.set("asientos", arr);

            String newEventJson = mapper.writeValueAsString(root);
            redis.opsForValue().set(key, newEventJson);

        } catch (Exception e) {
            throw new RuntimeException("Failed to upsert seat with timestamp in Redis", e);
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



/**
     * Intenta comprar (vender realmente) un asiento bloqueado por el usuario.
     * Si puede, marca el asiento como 'VENDIDO' y elimina el bloqueo.
     */
    public boolean intentarComprarAsiento(String eventoId, String seatId, String sessionId, String persona) {
        try {
            String key = keyForEvento(eventoId);
            String eventJson = redis.opsForValue().get(key);
            if (eventJson == null || eventJson.isBlank()) {
                return false;
            }

            ObjectNode root = (ObjectNode) mapper.readTree(eventJson);
            ArrayNode arr = root.withArray("asientos");
            long ahora = Instant.now().getEpochSecond();

            for (int i = 0; i < arr.size(); i++) {
                ObjectNode node = (ObjectNode) arr.get(i);

                // Reconstruir seatId si hiciera falta
                String sid = node.path("seatId").asText(null);
                if (sid == null || sid.isBlank()) {
                    var fNode = node.path("fila");
                    var cNode = node.path("columna");
                    if (fNode.isInt() && cNode.isInt()) {
                        sid = "r" + fNode.asInt() + "c" + cNode.asInt();
                    }
                }
                if (!seatId.equals(sid)) continue;

                String holder = node.path("holder").asText(null);

                // Validar expiración del bloqueo
                long expEpoch = 0;
                var expEpochNode = node.path("expiraEpoch");
                if (expEpochNode.isNumber()) expEpoch = expEpochNode.asLong();
                else {
                    String expIso = node.path("expira").asText(null);
                    if (expIso != null) {
                        try { expEpoch = ZonedDateTime.parse(expIso).toInstant().getEpochSecond(); }
                        catch (Exception ex) { expEpoch = 0; }
                    }
                }

                // Debe ser el dueño del bloqueo y no estar vencido
                if (holder == null || !holder.equals(sessionId)) return false;
                if (expEpoch != 0 && expEpoch <= ahora) return false;

                // Marcar como vendido
                ObjectNode merged = node.deepCopy();
                merged.put("status", "VENDIDO");
                merged.put("estado", "Vendido");

                ObjectNode compradorNode = mapper.createObjectNode();
                compradorNode.put("persona", persona != null ? persona : "");
                String fechaVenta = ZonedDateTime.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"))
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                compradorNode.put("fechaVenta", fechaVenta);
                merged.set("comprador", compradorNode);
                merged.put("fechaVenta", fechaVenta);

                merged.remove("holder");
                merged.remove("expira");
                merged.remove("expiraEpoch");
                merged.remove("updatedAt");
                merged.remove("updatedAtEpoch");

                arr.set(i, merged);
                root.set("asientos", arr);
                String nuevoJson = mapper.writeValueAsString(root);
                redis.opsForValue().set(key, nuevoJson);

                // Quitar la key de lock en Redis, si usás lockKeyForSeat
                String lockKey = lockKeyForSeat(eventoId, seatId);
                redis.delete(lockKey);

                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("intentarComprarAsiento error para {}:{} -> {}", eventoId, seatId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Devuelve el sessionId que tiene bloqueado un asiento, o null si no está bloqueado.
     */
    public String obtenerDueñoBloqueo(String eventoId, String seatId) {
        try {
            String lockKey = lockKeyForSeat(eventoId, seatId);
            return redis.opsForValue().get(lockKey);
        } catch (Exception e) {
            log.error("Error verificando dueño del bloqueo del asiento {}: {}", seatId, e.getMessage());
            return null;
        }
    }


    /** Helpers para keys en Redis */
    private String keyForEvento(String eventoId) {
        return "eventos:" + eventoId;
    }
    private String lockKeyForSeat(String eventoId, String seatId) {
        return "lock:" + eventoId + ":" + seatId;
    }

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
            updatedNode.put("owner", sessionId != null ? sessionId : "");
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


}
