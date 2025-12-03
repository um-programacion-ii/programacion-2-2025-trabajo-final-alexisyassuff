package com.cine.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.*;

/**
 * Servicio de reconciliación con soporte de dry-run, apply y auditoría en Redis.
 *
 * Reglas simples:
 * - Si las representaciones difieren, la que tenga updatedAt más reciente prevalece.
 * - Cuando se aplica, enviamos un publish/update al proxy para alinear su estado.
 * - Cada acción aplicada se registra en la lista Redis 'backend:reconciliation:applied'
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    private static final String AUDIT_LIST_KEY = "backend:reconciliation:applied";

    private final RestTemplate rest;
    private final String proxyBase;
    private final String catedraBase;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public ReconciliationService(RestTemplate restTemplate,
                                 @Value("${proxy.base-url:http://localhost:8081}") String proxyBase,
                                 @Value("${catedra.base-url:http://192.168.194.250:8080}") String catedraBase,
                                 StringRedisTemplate redis,
                                 ObjectMapper mapper) {
        this.rest = restTemplate;
        this.proxyBase = proxyBase;
        this.catedraBase = catedraBase;
        this.redis = redis;
        this.mapper = mapper;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> reconcileAndMaybeApply(String eventoId, boolean dryRun, boolean apply) {
        Map<String, Object> report = new HashMap<>();
        try {
            // 1) obtener seats desde proxy
            String proxyUrl = proxyBase + "/internal/kafka/events?eventoId=" + eventoId;
            List<Map<String, Object>> proxySeats = rest.getForObject(proxyUrl, List.class);
            Map<String, Map<String, Object>> proxyBySeat = new HashMap<>();
            if (proxySeats != null) {
                for (Map<String, Object> s : proxySeats) {
                    Object seatObj = s.get("seatId");
                    if (seatObj != null) proxyBySeat.put(seatObj.toString(), s);
                }
            }

            // 2) obtener seats desde cátedra
            String catedraUrl = catedraBase + "/internal/eventos/" + eventoId + "/asientos";
            List<Map<String, Object>> catedraSeats = null;
            try {
                catedraSeats = rest.getForObject(catedraUrl, List.class);
            } catch (Exception ex) {
                log.warn("No se pudo obtener asientos desde cátedra: {} - {}", catedraUrl, ex.toString());
            }
            Map<String, Map<String, Object>> catedraBySeat = new HashMap<>();
            if (catedraSeats != null) {
                for (Map<String, Object> s : catedraSeats) {
                    Object seatObj = s.get("seatId");
                    if (seatObj != null) catedraBySeat.put(seatObj.toString(), s);
                }
            }

            // 3) comparar
            Set<String> allSeats = new HashSet<>();
            allSeats.addAll(proxyBySeat.keySet());
            allSeats.addAll(catedraBySeat.keySet());

            List<Map<String, Object>> diffs = new ArrayList<>();

            for (String seatId : allSeats) {
                Map<String, Object> p = proxyBySeat.get(seatId);
                Map<String, Object> c = catedraBySeat.get(seatId);
                if (!Objects.equals(p, c)) {
                    Map<String, Object> d = new HashMap<>();
                    d.put("seatId", seatId);
                    d.put("proxy", p);
                    d.put("catedra", c);

                    // determine winner by updatedAt (if present)
                    Instant pTs = extractTs(p);
                    Instant cTs = extractTs(c);
                    if (pTs == null && cTs == null) {
                        d.put("winner", "unknown");
                    } else if (cTs == null || (pTs != null && pTs.isAfter(cTs))) {
                        d.put("winner", "proxy");
                    } else {
                        d.put("winner", "catedra");
                    }
                    diffs.add(d);
                }
            }

            report.put("eventoId", eventoId);
            report.put("diffCount", diffs.size());
            report.put("diffs", diffs);

            // 4) si apply=true, aplicar cambios según la política
            if (apply && !diffs.isEmpty()) {
                List<Map<String, Object>> applied = new ArrayList<>();
                for (Map<String, Object> diff : diffs) {
                    String seatId = diff.get("seatId").toString();
                    String winner = (String) diff.get("winner");
                    Map<String, Object> target = "catedra".equals(winner) ? (Map<String,Object>) diff.get("catedra") : (Map<String,Object>) diff.get("proxy");

                    if (target == null) {
                        log.info("No hay target para seat {} winner={}, saltando", seatId, winner);
                        continue;
                    }

                    Map<String, Object> publishPayload = buildPublishPayload(eventoId, seatId, target);
                    Map<String, Object> appliedEntry = new HashMap<>();
                    appliedEntry.put("seatId", seatId);
                    appliedEntry.put("winner", winner);
                    appliedEntry.put("payload", publishPayload);
                    appliedEntry.put("timestamp", Instant.now().toString());

                    try {
                        String publishUrl = proxyBase + "/internal/kafka/publish";
                        ResponseEntity<String> resp = rest.postForEntity(publishUrl, publishPayload, String.class);
                        appliedEntry.put("publishStatus", resp.getStatusCodeValue());
                        applied.add(appliedEntry);
                        log.info("Applied reconciliation for seat {} -> winner={}, proxy publish status={}", seatId, winner, resp.getStatusCodeValue());
                    } catch (Exception ex) {
                        log.error("Error al aplicar reconciliacion para seat {}: {}", seatId, ex.toString());
                        appliedEntry.put("error", ex.toString());
                        applied.add(appliedEntry);
                    }

                    // Persistir entrada de auditoría en Redis (no bloquear la apply si falla)
                    try {
                        String json = mapper.writeValueAsString(appliedEntry);
                        redis.opsForList().leftPush(AUDIT_LIST_KEY, json);
                    } catch (Exception ex) {
                        log.warn("No se pudo persistir auditoría en Redis para seat {}: {}", seatId, ex.toString());
                    }
                }
                report.put("applied", applied);
            }

        } catch (Exception e) {
            log.error("Error en reconciliación: {}", e.toString());
            report.put("error", e.toString());
        }
        return report;
    }

    private Instant extractTs(Map<String, Object> m) {
        if (m == null) return null;
        Object o = m.get("updatedAt");
        if (o == null) o = m.get("timestamp");
        if (o == null) return null;
        try {
            return Instant.parse(o.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> buildPublishPayload(String eventoId, String seatId, Map<String, Object> seat) {
        Map<String, Object> p = new HashMap<>();
        p.put("eventoId", tryParseLong(eventoId));
        p.put("asientoId", seatId);
        Object status = seat.getOrDefault("status", seat.get("estado"));
        Object holder = seat.getOrDefault("holder", seat.get("usuario"));
        Object updatedAt = seat.getOrDefault("updatedAt", seat.get("timestamp"));
        p.put("estado", status);
        p.put("usuario", holder);
        p.put("timestamp", updatedAt);
        return p;
    }

    private Object tryParseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception ignored) { return s; }
    }
}