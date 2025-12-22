package com.cine.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class EventoInitializationService {

    private static final Logger log = LoggerFactory.getLogger(EventoInitializationService.class);

    private final StringRedisTemplate redis;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;
    private final String catedraBase;

    public EventoInitializationService(StringRedisTemplate redis, 
                                     RestTemplate restTemplate,
                                     ObjectMapper mapper,
                                     @Value("${catedra.base-url:http://192.168.194.250:8080}") String catedraBase) {
        this.redis = redis;
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.catedraBase = catedraBase;
    }


    @SuppressWarnings("unchecked")
    public boolean ensureEventoInitialized(String eventoId) {
        String redisKey = "evento_" + eventoId;
        
        try {
            // 1. Verificar si ya existe en Redis
            Boolean exists = redis.hasKey(redisKey);
            if (Boolean.TRUE.equals(exists)) {
                log.debug("Evento {} ya existe en Redis, saltando inicialización", eventoId);
                return true;
            }
            
            // 2. Consultar datos del evento desde Cátedra
            String catedraUrl = catedraBase + "/eventos/" + eventoId;
            log.info("Consultando evento desde Cátedra: {}", catedraUrl);
            
            Map<String, Object> eventoData = restTemplate.getForObject(catedraUrl, Map.class);
            if (eventoData == null) {
                log.error("No se pudo obtener datos del evento {} desde Cátedra", eventoId);
                return false;
            }
            
            // 3. Extraer dimensiones de la sala
            Integer filas = null;
            Integer columnas = null;
            
            try {
                Object filasObj = eventoData.get("filas");
                Object columnasObj = eventoData.get("columnas");
                
                if (filasObj instanceof Number) {
                    filas = ((Number) filasObj).intValue();
                }
                if (columnasObj instanceof Number) {
                    columnas = ((Number) columnasObj).intValue();
                }
            } catch (Exception e) {
                log.error("Error extrayendo dimensiones del evento {}: {}", eventoId, e.getMessage());
            }
            
            if (filas == null || columnas == null || filas <= 0 || columnas <= 0) {
                log.error("Dimensiones inválidas para evento {}: filas={}, columnas={}", eventoId, filas, columnas);
                return false;
            }
            
            log.info("Inicializando evento {} con dimensiones {}x{}", eventoId, filas, columnas);
            
            // 4. Generar matriz completa de asientos como "Libre"
            List<Map<String, Object>> asientos = new ArrayList<>();
            Instant now = Instant.now();
            
            for (int fila = 1; fila <= filas; fila++) {
                for (int columna = 1; columna <= columnas; columna++) {
                    Map<String, Object> asiento = new HashMap<>();
                    asiento.put("fila", fila);
                    asiento.put("columna", columna);
                    asiento.put("estado", "Libre");
                    asiento.put("comprador", null);
                    asiento.put("fechaVenta", null);
                    asiento.put("updatedAt", now.toString());
                    asientos.add(asiento);
                }
            }
            
            // 5. Crear estructura completa del evento
            Map<String, Object> eventoCompleto = new HashMap<>();
            eventoCompleto.put("eventoId", Integer.parseInt(eventoId));
            eventoCompleto.put("filas", filas);
            eventoCompleto.put("columnas", columnas);
            eventoCompleto.put("asientos", asientos);
            eventoCompleto.put("initializedAt", now.toString());
            
            // 6. Persistir en Redis como JSON
            String jsonData = mapper.writeValueAsString(eventoCompleto);
            redis.opsForValue().set(redisKey, jsonData);
            
            log.info("Evento {} inicializado en Redis con {} asientos ({}x{})", 
                    eventoId, asientos.size(), filas, columnas);
            
            return true;
            
        } catch (Exception e) {
            log.error("Error inicializando evento {} en Redis: {}", eventoId, e.getMessage(), e);
            return false;
        }
    }
}
