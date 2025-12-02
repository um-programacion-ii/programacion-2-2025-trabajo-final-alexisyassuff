package com.cine.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Servicio de reconciliación básico.
 * - Obtiene asientos desde proxy (GET /internal/kafka/events?eventoId=)
 * - Obtiene información desde cátedra (configurable base URL + endpoint)
 * - Compara por seatId y retorna un reporte con las diferencias encontradas.
 *
 * Ajustá las URIs según la API real de la cátedra/proxy si difieren.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final RestTemplate rest;
    private final String proxyBase;
    private final String catedraBase;

    public ReconciliationService(RestTemplate restTemplate,
                                 @Value("${proxy.base-url:http://localhost:8081}") String proxyBase,
                                 @Value("${catedra.base-url:http://192.168.194.250:8080}") String catedraBase) {
        this.rest = restTemplate;
        this.proxyBase = proxyBase;
        this.catedraBase = catedraBase;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> reconcile(String eventoId) {
        Map<String, Object> report = new HashMap<>();
        try {
            // 1) proxy seats
            String proxyUrl = proxyBase + "/internal/kafka/events?eventoId=" + eventoId;
            List<Map<String, Object>> proxySeats = rest.getForObject(proxyUrl, List.class);
            Map<String, Map<String, Object>> proxyBySeat = new HashMap<>();
            if (proxySeats != null) {
                for (Map<String, Object> s : proxySeats) {
                    Object seatObj = s.get("seatId");
                    if (seatObj != null) {
                        proxyBySeat.put(seatObj.toString(), s);
                    }
                }
            }

            // 2) catedra seats (ajustar endpoint si es distinto)
            String catedraUrl = catedraBase + "/internal/eventos/" + eventoId + "/asientos";
            List<Map<String, Object>> catedraSeats = null;
            try {
                catedraSeats = rest.getForObject(catedraUrl, List.class);
            } catch (Exception ex) {
                log.warn("No se pudo obtener asientos desde cátedra (endpoint {}). Excepción: {}", catedraUrl, ex.toString());
            }
            Map<String, Map<String, Object>> catedraBySeat = new HashMap<>();
            if (catedraSeats != null) {
                for (Map<String, Object> s : catedraSeats) {
                    Object seatObj = s.get("seatId");
                    if (seatObj != null) {
                        catedraBySeat.put(seatObj.toString(), s);
                    }
                }
            }

            // 3) compare
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
                    diffs.add(d);
                }
            }

            report.put("eventoId", eventoId);
            report.put("diffCount", diffs.size());
            report.put("diffs", diffs);
        } catch (Exception e) {
            log.error("Error en reconciliación: {}", e.toString());
            report.put("error", e.toString());
        }
        return report;
    }
}