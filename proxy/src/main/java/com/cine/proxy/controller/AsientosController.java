package com.cine.proxy.controller;
import com.cine.proxy.model.Seat;
import com.cine.proxy.service.RedisSeatService;
import com.cine.proxy.service.SessionTokenValidatorService;
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
import java.time.OffsetDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

@RestController
public class AsientosController {
    private static final Logger log = LoggerFactory.getLogger(AsientosController.class);

    private final RedisSeatService seatService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;
    private final String backendBaseUrl;

    @Autowired
    private SessionTokenValidatorService sessionTokenValidatorService;

    public AsientosController(RedisSeatService seatService, StringRedisTemplate redis,
                             @Value("${backend.base-url:http://localhost:8080}") String backendBaseUrl) {
        this.seatService = seatService;
        this.redis = redis;
        this.restTemplate = new RestTemplate();
        this.backendBaseUrl = backendBaseUrl.endsWith("/") ? backendBaseUrl.substring(0, backendBaseUrl.length()-1) : backendBaseUrl;
    }


    @GetMapping("/asientos/{eventoId}")
    public ResponseEntity<List<Map<String,Object>>> getAsientos(
            @PathVariable String eventoId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        
        try {
            int[] dims = obtenerDimensionesEvento(eventoId);
            int filas = dims[0], columnas = dims[1];
            List<Map<String,Object>> allSeats = generateBaseMatrix(filas, columnas);
            mergeRedisStates(allSeats, eventoId, sessionId);
            return ResponseEntity.ok(allSeats);

        } catch (Exception ex) {
            log.error("Error generando matriz de asientos para evento {}: {}", eventoId, ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Error interno: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of(err));
        }
    }


    private int[] obtenerDimensionesEvento(String eventoId) {
        String eventosJson = null;
        try {
            eventosJson = restTemplate.getForObject("http://localhost:8080/api/endpoints/v1/eventos", String.class);
        } catch (Exception e) {
            log.warn("No se pudo obtener la lista de eventos del backend local: {}", e.getMessage());
            eventosJson = null;
        }
        int filas = 0, columnas = 0;
        if (eventosJson != null && !eventosJson.isBlank()) {
            try {
                JsonNode eventosArray = objectMapper.readTree(eventosJson);
                for (JsonNode evento : eventosArray) {
                    if (evento.has("id") && evento.path("id").asText().equals(eventoId)) {
                        filas = evento.path("filas").asInt(0);
                        columnas = evento.path("columnas").asInt(0);
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("Error parseando el JSON de eventos: {}", e.getMessage());
                filas = 0;
                columnas = 0;
            }
        }
        return new int[]{filas, columnas};
    }

    private List<Map<String, Object>> generateBaseMatrix(int filas, int columnas) {
        List<Map<String, Object>> allSeats = new ArrayList<>();
        for (int fila = 1; fila <= filas; fila++) {
            for (int columna = 1; columna <= columnas; columna++) {
                String seatId = String.format("r%dc%d", fila, columna);
                Map<String, Object> seat = new HashMap<>();
                seat.put("seatId", seatId);
                seat.put("status", "LIBRE");
                seat.put("fila", fila);
                seat.put("columna", columna);
                allSeats.add(seat);
            }
        }
        log.info("Generada matriz base de {} asientos LIBRE", allSeats.size());
        return allSeats;
    }

    private void mergeRedisStates(List<Map<String, Object>> allSeats, String eventoId, String sessionId) {
        String redisKey = "evento_" + eventoId;
        String redisData = null;
        try {
            redisData = redis.opsForValue().get(redisKey);
        } catch (Exception e) {
            log.error("Error leyendo key {} desde Redis: {}", redisKey, e.getMessage());
            redisData = null;
        }
      
        long timeNow = java.time.Instant.now().getEpochSecond();
        if (redisData != null && !redisData.trim().isEmpty()) {
            try {
                JsonNode redisRoot = objectMapper.readTree(redisData);
                JsonNode asientos = redisRoot.path("asientos");

                if (asientos.isArray()) {
                    for (JsonNode asiento : asientos) {
                        int fila = asiento.path("fila").asInt(-1);
                        int columna = asiento.path("columna").asInt(-1);
                        String estado = asiento.path("estado").asText(null);

                        String status = "LIBRE";
                        if ("Vendido".equalsIgnoreCase(estado)) {
                            status = "VENDIDO";
                        } else if ("Bloqueado".equalsIgnoreCase(estado)) {
                                String expiracion = asiento.path("expira").asText(null);
                                if (expiracion != null) {
                                    long expiracionEpoch = OffsetDateTime
                                            .parse(expiracion)
                                            .toInstant()
                                            .getEpochSecond();
                                    if (expiracionEpoch > timeNow) {
                                        status = "BLOQUEADO";
                                    } else {
                                        status = "LIBRE";
                                    }
                                } else {
                                    status = "LIBRE";
                                }
                        }

                        for (Map<String, Object> seat : allSeats) {
                            Integer seatFila = (Integer) seat.get("fila");
                            Integer seatColumna = (Integer) seat.get("columna");

                            if (seatFila != null && seatColumna != null && seatFila.equals(fila) && seatColumna.equals(columna)) {
                                String prev = (String) seat.get("status");
                                if (!"VENDIDO".equals(prev)) {
                                    seat.put("status", status);
                                    if ("BLOQUEADO".equals(status)) {
                                        String holder = asiento.path("holder").asText(null);
                                        if (holder != null && !holder.isBlank()) {
                                            seat.put("holder", holder);
                                        } else {
                                            seat.remove("holder");
                                        }
                                    } else if ("VENDIDO".equals(status)) {
                                        JsonNode compradorNode = asiento.path("comprador");
                                        if (!compradorNode.isMissingNode() && !compradorNode.isNull() && compradorNode.isObject()) {
                                            Map<String, Object> compradorMap = new HashMap<>();
                                            compradorMap.put("persona", compradorNode.path("persona").asText(""));
                                            compradorMap.put("fechaVenta", compradorNode.path("fechaVenta").asText(""));
                                            seat.put("comprador", compradorMap);
                                        } else {
                                            seat.put("comprador", "");
                                        }
                                    }
                                }
                                break;
                            }
                        }
                    }
                } else {
                    log.warn("Datos de Redis no contienen array 'asientos' válido para evento {}", eventoId);
                }
            } catch (Exception e) {
                log.error("Error parseando/mergeando datos JSON de Redis para {}: {}", eventoId, e.getMessage(), e);
            }
        }
    }

  

    @PostMapping("/api/endpoints/v1/bloquear-asiento")
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

            // Parsear seatId a fila/columna esperado por la cátedra
            int fila, columna;
            if (seatId != null && seatId.startsWith("r") && seatId.contains("c")) {
                String[] parts = seatId.substring(1).split("c", 2);
                fila = Integer.parseInt(parts[0]);
                columna = Integer.parseInt(parts[1]);
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Formato de seatId inválido (esperado r{fila}c{columna})"));
            }

            // PREPARA EL PAYLOAD que la cátedra espera (Payload 6)
            Map<String, Object> asiento = Map.of(
                "fila", fila,
                "columna", columna
            );
            Map<String, Object> catedraBody = Map.of(
                "eventoId", eventoId,
                "asientos", List.of(asiento)
            );

            // Headers con BEARER TOKEN CÁTEDRA
            String catedraUrl = "http://192.168.194.250:8080/api/endpoints/v1/bloquear-asientos";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth("eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhbHVfMTc2NDQyOTYzOSIsImV4cCI6MTc2NzAyMTY0MCwiYXV0aCI6IlJPTEVfVVNFUiIsImlhdCI6MTc2NDQyOTY0MH0.NcLOxLcORQetlqFzkFDOz1tBlaA6H0bVHq4lOyJ9mEs4plIrgdiG6G_IIaQU8UNMAeyGSVkJQ_129Hkt1dOFCA");

            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<?> entity = new HttpEntity<>(catedraBody, headers);

            ResponseEntity<Map> catedraResp = restTemplate.postForEntity(catedraUrl, entity, Map.class);

            // Analiza la respuesta de la cátedra
            Map catedraResult = catedraResp.getBody();
            boolean resultado = catedraResult != null && Boolean.TRUE.equals(catedraResult.get("resultado"));

            if (resultado) {
                // Sólo si fue OK en cátedra, bloquea en tu Redis local:
                boolean blocked = seatService.tryBlockSeatWithTTL(String.valueOf(eventoId), seatId, sessionId);

                if (blocked) {
                    Map<String, Object> ok = new HashMap<>();
                    ok.put("message", "Asiento bloqueado. Tiene 5 minutos para comprar.");
                    ok.put("respuesta_catedra", catedraResult);
                    return ResponseEntity.ok(ok);
                } else {
                    String lockOwner = seatService.getSeatLockOwner(String.valueOf(eventoId), seatId);
                    if (lockOwner != null) {
                        Map<String, Object> conflict = new HashMap<>();
                        conflict.put("error", "BLOCKED_BY_OTHER");
                        conflict.put("owner", lockOwner);
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(conflict);
                    } else {
                        Map<String, Object> unavailable = new HashMap<>();
                        unavailable.put("error", "SEAT_NOT_AVAILABLE");
                        unavailable.put("message", "Asiento no disponible para bloqueo local (posiblemente vendido)");
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(unavailable);
                    }
                }
            } else {
                // Si cátedra no dejó bloquear, devolvemos el JSON tal cual
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "No se pudo bloquear en cátedra",
                                "detalle_catedra", catedraResult));
            }
        } catch (Exception ex) {
            log.error("Error blockSeatForPurchase: {}", ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "internal");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }
   
    @PostMapping("/api/endpoints/v1/realizar-venta")
    public ResponseEntity<?> venderAsiento(
        @RequestBody Map<String, Object> request,
        @RequestHeader(value = "X-Session-Id", required = false) String sessionId)
        
        
         {
        try {
            System.out.println("[DEBUG] Token recibido en header: " + sessionId);
            if (sessionId == null || sessionId.isBlank() || !sessionTokenValidatorService.isSessionTokenValidRemoto(sessionId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid X-Session-Id"));
            }

            int eventoId = (int) request.get("eventoId");
            String seatId = (String) request.get("seatId");
            String persona = (String) request.getOrDefault("persona", "Sin nombre");
            Double precioVenta = request.get("precioVenta") != null ? Double.valueOf(request.get("precioVenta").toString()) : 1000.0;

            int fila, columna;
            if (seatId != null && seatId.startsWith("r") && seatId.contains("c")) {
                String[] parts = seatId.substring(1).split("c", 2);
                fila = Integer.parseInt(parts[0]);
                columna = Integer.parseInt(parts[1]);
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Formato de seatId inválido (esperado r{fila}c{columna})"));
            }

            boolean compradoYBloqueado = seatService.intentarComprarAsiento(String.valueOf(eventoId), seatId, sessionId, persona);
            if (!compradoYBloqueado) {
                String lockOwner = seatService.obtenerDueñoBloqueo(String.valueOf(eventoId), seatId);
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

            // PREPARA EL PAYLOAD PARA LA CATEDRA
            Map<String, Object> asientoCatedra = Map.of(
                "fila", fila,
                "columna", columna,
                "persona", persona
            );
            Map<String, Object> catedraBody = Map.of(
                "eventoId", eventoId,
                "fecha", java.time.Instant.now().toString(),
                "precioVenta", precioVenta,
                "asientos", List.of(asientoCatedra)
            );
            // POST a la cátedra con Bearer Token
            String catedraUrl = "http://192.168.194.250:8080/api/endpoints/v1/realizar-venta";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth("eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhbHVfMTc2NDQyOTYzOSIsImV4cCI6MTc2NzAyMTY0MCwiYXV0aCI6IlJPTEVfVVNFUiIsImlhdCI6MTc2NDQyOTY0MH0.NcLOxLcORQetlqFzkFDOz1tBlaA6H0bVHq4lOyJ9mEs4plIrgdiG6G_IIaQU8UNMAeyGSVkJQ_129Hkt1dOFCA");

            HttpEntity<?> entity = new HttpEntity<>(catedraBody, headers);
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> catedraResp = restTemplate.postForEntity(catedraUrl, entity, Map.class);

            Map catedraResult = catedraResp.getBody();
            boolean resultado = catedraResult != null && Boolean.TRUE.equals(catedraResult.get("resultado"));

            // Solo persistir local si la cátedra responde OK
            if (resultado) {
                // Llamada al backend local para guardar la venta
                notificarVentaAlBackend(persona, eventoId, seatId,
                    Map.of("fila", fila, "columna", columna), precioVenta);

                Map<String, Object> ok = new HashMap<>();
                ok.put("result", "venta_guardada_en_catedra");
                ok.put("seatId", seatId);
                // ok.put("owner", sessionId);
                ok.put("comprador", Map.of("persona", persona));
                ok.put("ventaId_catedra", catedraResult.get("ventaId"));
                ok.put("fechaVenta", catedraResult.getOrDefault("fechaVenta", java.time.Instant.now().toString()));
                ok.put("respuesta_catedra", catedraResult);
                return ResponseEntity.ok(ok);
            } else {
                // Devolver detalle literal al usuario
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Venta rechazada en cátedra", "detalle_catedra", catedraResult));
            }

        } catch (Exception ex) {
            log.error("Error purchaseSeat: {}", ex.getMessage(), ex);
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

   

    /**
     * Notifica al backend para que persista la venta individual confirmada.
     * Solo se llama después de que la cátedra confirme la venta exitosamente.
     */
    private void notificarVentaAlBackend(String persona, int eventoId, String seatId,
                                        Map<String, Object> filaColumna, Double precio) {
        try {
            String backendUrl = "http://localhost:8080/api/endpoints/v1/realizar-venta";
            Map<String, Object> payload = new HashMap<>();
            payload.put("usuario", persona); 
            payload.put("eventoId", eventoId);
            payload.put("seatId", seatId);
            payload.put("fila", filaColumna.get("fila"));
            payload.put("columna", filaColumna.get("columna"));
            payload.put("total", precio);
            payload.put("precio", precio);
            payload.put("precioVenta", precio);
            payload.put("fechaVenta", java.time.Instant.now().toString());

            log.info("Notificando venta individual al backend: {}", payload);

            RestTemplate restTemplate = new RestTemplate();
            restTemplate.postForObject(backendUrl, payload, Map.class);
            log.info("Venta individual persistida exitosamente en el backend");

        } catch (Exception e) {
            log.error("Error notificando venta individual al backend: {}", e.getMessage(), e);
            // NO fallar - la venta ya está confirmada en cátedra y Redis
        }
    }


}