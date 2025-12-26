package com.cine.proxy.controller;

import com.cine.proxy.config.CatedraProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
public class EventsController {

    private static final Logger log = LoggerFactory.getLogger(EventsController.class);

    private final RestTemplate restTemplate;
    private final CatedraProperties catedraProperties;

    public EventsController(CatedraProperties catedraProperties) {
        this.restTemplate = new RestTemplate();
        this.catedraProperties = catedraProperties;
    }


    @GetMapping("/eventos/{id}")
    public ResponseEntity<?> getEventoFromCatedra(@PathVariable String id) {
        try {
            String catedraUrl = "http://192.168.194.250:8080/api/eventos/" + id;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + catedraProperties.getToken());
            
            org.springframework.http.HttpEntity<?> entity = new org.springframework.http.HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    catedraUrl,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Proxy: Evento {} obtenido exitosamente desde Cátedra", id);
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(response.getBody());
            } else {
                log.warn("Proxy: Cátedra devolvió status {} para evento {}", response.getStatusCode(), id);
                return ResponseEntity.status(response.getStatusCode())
                        .body(Map.of("error", "Error obteniendo evento desde cátedra"));
            }
            
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Proxy: Error HTTP obteniendo evento {} desde Cátedra: {} - {}", id, e.getStatusCode(), e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("error", e.getMessage(), "status", e.getStatusCode().value()));
        } catch (Exception ex) {
            log.error("Proxy: Error inesperado obteniendo evento {} desde Cátedra: {}", id, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del proxy: " + ex.getMessage()));
        }
    }
}

