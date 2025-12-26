package com.cine.backend.service;

import com.cine.backend.model.Evento;
import com.cine.backend.repository.EventoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
    private final String proxyBase;
    private final EventoRepository eventoRepository;

    public EventoInitializationService(StringRedisTemplate redis, 
                                     RestTemplate restTemplate,
                                     ObjectMapper mapper,
                                     EventoRepository eventoRepository,
                                     @Value("${proxy.base-url:http://localhost:8081}") String proxyBase) {
        this.redis = redis;
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.eventoRepository = eventoRepository;
        this.proxyBase = proxyBase.endsWith("/") ? proxyBase.substring(0, proxyBase.length()-1) : proxyBase;
    }

    @SuppressWarnings("unchecked")
    public boolean ensureEventoInitialized(String eventoId) {
        String redisKey = "evento_" + eventoId;
        Long eventoIdLong = Long.parseLong(eventoId);
        
        try {
            if (eventoRepository.existsById(eventoIdLong)) {
                log.debug("Evento {} ya existe en BD local, saltando inicialización", eventoId);
                Boolean exists = redis.hasKey(redisKey);
                if (!Boolean.TRUE.equals(exists)) {
                    log.debug("Evento {} existe en BD pero no en Redis, inicializando Redis", eventoId);
                }
                return true;
            }

            String proxyUrl = proxyBase + "/eventos/" + eventoId;
            log.info("Consultando evento desde Proxy: {}", proxyUrl);

            String responseBody = null;
            try {
                responseBody = restTemplate.getForObject(proxyUrl, String.class);
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                log.error("Error HTTP al consultar evento {} desde Proxy: {} - {}", eventoId, e.getStatusCode(), e.getMessage());
                return false;
            } catch (org.springframework.web.client.UnknownContentTypeException e) {
                log.error("Error: Proxy devolvió contenido no-JSON para evento {}: {}", eventoId, e.getMessage());
                if (e.getResponseBodyAsString() != null) {
                    responseBody = e.getResponseBodyAsString();
                    log.warn("Respuesta recibida (puede ser HTML): {}", responseBody.substring(0, Math.min(200, responseBody.length())));
                }
                return false;
            } catch (Exception e) {
                log.error("Error inesperado al consultar evento {} desde Proxy: {}", eventoId, e.getMessage(), e);
                return false;
            }

            if (responseBody == null || responseBody.trim().isEmpty()) {
                log.error("No se pudo obtener datos del evento {} desde Proxy (respuesta vacía)", eventoId);
                return false;
            }

            Map<String, Object> eventoData = null;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = mapper.readValue(responseBody, Map.class);

                if (parsed.containsKey("evento") && parsed.get("evento") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> eventoWrapper = (Map<String, Object>) parsed.get("evento");
                    eventoData = eventoWrapper;
                    log.debug("Evento {} viene envuelto en objeto 'evento'", eventoId);
                } else {
                    eventoData = parsed;
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.error("Error parseando JSON del evento {} desde Proxy: {}", eventoId, e.getMessage());
                log.error("Respuesta recibida (primeros 500 chars): {}", 
                    responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);
                return false;
            }

            if (eventoData == null) {
                log.error("No se pudo parsear datos del evento {} desde Proxy", eventoId);
                return false;
            }

            log.debug("Evento {} parseado correctamente desde Proxy", eventoId);
            log.debug("Campos disponibles en eventoData: {}", eventoData.keySet());

            String titulo = extractString(eventoData, "titulo");
            String descripcion = extractString(eventoData, "descripcion");
            String imagen = extractString(eventoData, "imagen");
            Double precio = extractDouble(eventoData, "precioEntrada");
            LocalDateTime fecha = extractFecha(eventoData, "fecha");
            Integer filas = extractInteger(eventoData, "filaAsientos");
            Integer columnas = extractInteger(eventoData, "columnaAsientos");

            log.debug("Dimensiones extraídas para evento {}: filas={}, columnas={}", eventoId, filas, columnas);

            if (filas == null || columnas == null || filas <= 0 || columnas <= 0) {
                log.error("Dimensiones inválidas para evento {}: filas={}, columnas={}", eventoId, filas, columnas);
                return false;
            }

            log.info("Inicializando evento {} con dimensiones {}x{}", eventoId, filas, columnas);

            Evento evento = new Evento(eventoIdLong, titulo, descripcion, precio, fecha, filas, columnas, imagen);
            eventoRepository.save(evento);
            
            return true; 

        } catch (Exception e) {
            log.error("Error inicializando evento {}: {}", eventoId, e.getMessage(), e);
            return false;
        }
    }

    private String extractString(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return keys.length > 0 ? "" : null;
    }

    private Double extractDouble(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null) {
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                try {
                    return Double.parseDouble(value.toString());
                } catch (NumberFormatException e) {}
            }
        }
        return 0.0;
    }

    private Integer extractInteger(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null) {
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
                try {
                    return Integer.parseInt(value.toString());
                } catch (NumberFormatException e) {
                    try {
                        double d = Double.parseDouble(value.toString());
                        return (int) d;
                    } catch (NumberFormatException e2) {}
                }
            }
        }
        return null;
    }

    private LocalDateTime extractFecha(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null) {
                String fechaStr = value.toString();
                if (fechaStr != null && !fechaStr.trim().isEmpty()) {
                    try {
                        if (fechaStr.endsWith("Z")) {
                            fechaStr = fechaStr.substring(0, fechaStr.length() - 1);
                        }

                        DateTimeFormatter[] formatters = {
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
                        };

                        for (DateTimeFormatter formatter : formatters) {
                            try {
                                return LocalDateTime.parse(fechaStr, formatter);
                            } catch (DateTimeParseException e) {}
                        }

                        if (fechaStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                            return LocalDateTime.parse(fechaStr + "T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        }
                    } catch (Exception e) {
                        log.warn("No se pudo parsear fecha '{}': {}", fechaStr, e.getMessage());
                    }
                }
            }
        }
        return LocalDateTime.now();
    }
}