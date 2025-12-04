package com.cine.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Cliente simple que llama al proxy para obtener:
 * - datos de la cátedra: GET {proxyBase}/eventos/{id}
 * - estado desde Redis (vía proxy): GET {proxyBase}/internal/kafka/events?eventoId={id}
 *
 * La base URL se inyecta desde application.yml con la propiedad proxy.base-url.
 */
@Service
public class BackendProxyClient {

    private static final Logger log = LoggerFactory.getLogger(BackendProxyClient.class);

    private final RestTemplate rest;
    private final String proxyBase;

    public BackendProxyClient(RestTemplate restTemplate,
                              @Value("${proxy.base-url:http://localhost:8081}") String proxyBase) {
        this.rest = restTemplate;
        this.proxyBase = proxyBase.endsWith("/") ? proxyBase.substring(0, proxyBase.length()-1) : proxyBase;
    }

    public Map<String, Object> getCombinedEventoView(String eventoId) {
        String catUrl = proxyBase + "/eventos/" + eventoId;
        String seatsUrl = proxyBase + "/internal/kafka/events?eventoId=" + eventoId;

        log.debug("Llamando a proxy: catedra={} seats={}", catUrl, seatsUrl);

        ResponseEntity<String> catResp = rest.exchange(catUrl, HttpMethod.GET, new HttpEntity<>(defaultHeaders()), String.class);
        ResponseEntity<String> seatsResp = rest.exchange(seatsUrl, HttpMethod.GET, new HttpEntity<>(defaultHeaders()), String.class);

        return Map.of(
                "catedra", catResp.getBody(),
                "seats", seatsResp.getBody()
        );
    }

    private HttpHeaders defaultHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}