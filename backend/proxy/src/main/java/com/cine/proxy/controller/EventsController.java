package com.cine.proxy.controller;

import com.cine.proxy.client.CatedraClient;
import com.cine.proxy.client.CatedraException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller exposing endpoints to the proxy clients.
 * - GET /eventos and GET /eventos/{id} proxied to the cátedra via CatedraClient.
 * - POST /eventos/{id}/bloquear -> forwards to /api/endpoints/v1/bloquear-asientos
 * - POST /eventos/{id}/vender   -> forwards to /api/endpoints/v1/realizar-venta
 *
 * The controller enriches the incoming body with eventoId taken from the path variable.
 */
@RestController
@RequestMapping("/eventos")
public class EventsController {

    private static final Logger log = LoggerFactory.getLogger(EventsController.class);

    private final CatedraClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public EventsController(CatedraClient client) {
        this.client = client;
    }

    @GetMapping
    public ResponseEntity<?> listEventos() {
        String body = client.listEventos();
        return ResponseEntity.ok().body(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getEvento(@PathVariable String id) {
        String body = client.getEvento(id);
        return ResponseEntity.ok().body(body);
    }

    /**
     * POST /eventos/{id}/bloquear
     * Upstream: POST /api/endpoints/v1/bloquear-asientos
     * Expected upstream body: { "eventoId": <id>, "asientos": [ { "fila":.., "columna":.. }, ... ] }
     *
     * We accept any JSON object from the client, ensure eventoId is set, and forward the JSON.
     */
    @PostMapping("/{id}/bloquear")
    public ResponseEntity<?> bloquearAsiento(@PathVariable("id") Integer id,
                                             @RequestBody Map<String, Object> payload) {
        try {
            Map<String, Object> upstream = new HashMap<>(payload == null ? Map.of() : payload);
            upstream.put("eventoId", id);
            String json = mapper.writeValueAsString(upstream);

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
     * POST /eventos/{id}/vender
     * Upstream: POST /api/endpoints/v1/realizar-venta
     * Expected upstream body: {
     *   "eventoId": <id>,
     *   "fecha": "...",
     *   "precioVenta": ...,
     *   "asientos": [ { "fila":.., "columna":.., "persona": "..." } , ... ]
     * }
     */
    @PostMapping("/{id}/vender")
    public ResponseEntity<?> venderAsiento(@PathVariable("id") Integer id,
                                           @RequestBody Map<String, Object> payload) {
        try {
            Map<String, Object> upstream = new HashMap<>(payload == null ? Map.of() : payload);
            upstream.put("eventoId", id);
            String json = mapper.writeValueAsString(upstream);

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
}