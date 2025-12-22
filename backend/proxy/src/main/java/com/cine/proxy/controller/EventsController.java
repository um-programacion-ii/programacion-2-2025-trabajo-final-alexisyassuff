package com.cine.proxy.controller;
import com.cine.proxy.model.Seat;
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
import java.util.Set;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class EventsController {
    private static final Logger log = LoggerFactory.getLogger(EventsController.class);

    private final CatedraClient client;
    private final RedisSeatService seatService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private StringRedisTemplate redis; // Inyectar la instancia de RedisTemplate

    public EventsController(CatedraClient client, RedisSeatService seatService) {
        this.client = client;
        this.seatService = seatService;
    }


    @GetMapping("/api/endpoints/v1/listar-ventas")
    public ResponseEntity<?> listarMisVentas() {
        try {
            log.info("Iniciando endpoint para listar mis ventas...");

            // Datos finales de las ventas realizadas por vos
            List<Map<String, Object>> misVentas = new ArrayList<>();

            // Recuperamos las claves de eventos
            List<String> eventos = List.of("evento_1", "evento_2", "evento_3", "evento_4", "evento_5");

            for (String evento : eventos) {
                try {
                    log.info("Buscando ventas para {}", evento);

                    String eventJson = redis.opsForValue().get(evento);
                    if (eventJson == null || eventJson.isBlank()) {
                        log.warn("No se encontraron datos en Redis para {}", evento);
                        continue; // Saltar si no existen datos en Redis para este evento
                    }

                    // Parsear el contenido del evento
                    com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(eventJson);
                    com.fasterxml.jackson.databind.JsonNode asientos = root.path("asientos");

                    // Extraer y procesar los asientos con status "VENDIDO"
                    for (com.fasterxml.jackson.databind.JsonNode asiento : asientos) {
                        String seatId = asiento.path("seatId").asText();
                        if (seatId == null || seatId.isBlank()) {
                            log.warn("Se encontró un asiento sin ID en el evento {}", evento);
                            continue;
                        }

                        String status = asiento.path("status").asText();
                        if (!"VENDIDO".equals(status)) {
                            continue; // Ignorar asientos que no son vendidos
                        }

                        String fechaVenta = asiento.path("fechaVenta").asText();
                        if (fechaVenta == null || fechaVenta.isBlank()) {
                            log.warn("El asiento {} no tiene fecha de venta en el evento {}", seatId, evento);
                            continue;
                        }

                        String comprador = asiento.path("comprador").path("persona").asText(""); // Comprador si existe

                        // Armar el mapa con los datos de la venta
                        Map<String, Object> venta = new HashMap<>();
                        venta.put("evento", evento);
                        venta.put("asiento", seatId);
                        venta.put("fechaVenta", fechaVenta);
                        if (!comprador.isBlank()) {
                            venta.put("comprador", comprador);
                        }

                        misVentas.add(venta);
                    }
                } catch (Exception ex) {
                    log.error("Error procesando el evento {}: {}", evento, ex.getMessage(), ex);
                }
            }

            log.info("Total de mis ventas procesadas: {}", misVentas.size());
            return ResponseEntity.ok(misVentas);

        } catch (Exception e) {
            log.error("Error inesperado al listar mis ventas: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error inesperado al listar mis ventas"));
        }
    }


    @GetMapping("/api/endpoints/v1/eventos")
    public ResponseEntity<?> listEventosEnunciado() {
        String body = client.listEventos();
        return ResponseEntity.ok().body(body);
    }

    @GetMapping("/api/endpoints/v1/evento/{id}")
    public ResponseEntity<?> getDetalleEvento(@PathVariable String id) {
        try {
            // 1. Obtener datos del evento
            String eventoData = client.getEvento(id);
            return ResponseEntity.ok(eventoData);
            
        } catch (Exception ex) {
            log.error("Error obteniendo evento {}: {}", id, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"Error interno: " + ex.getMessage() + "\"}");
        }
    }
    

    @GetMapping("/api/endpoints/v1/evento/{id}/asientos")
    public ResponseEntity<?> getEstadoAsientos(@PathVariable String id) {
        try {
            // Redis ya está poblado - solo leer asientos existentes
            
            // 2. Obtener todos los asientos del evento
            java.util.List<com.cine.proxy.model.Seat> seats = seatService.getSeatsForEvento(id);
            
            return ResponseEntity.ok(seats);
            
        } catch (Exception ex) {
            log.error("Error obteniendo asientos enunciado para evento {}: {}", id, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"Error interno: " + ex.getMessage() + "\"}");
        }
    }

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