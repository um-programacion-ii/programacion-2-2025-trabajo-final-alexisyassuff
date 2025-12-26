package com.cine.backend.controller;

import com.cine.backend.model.Venta;
import com.cine.backend.model.VentaAsiento;
import com.cine.backend.service.VentaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controller para manejar ventas confirmadas.
 * 
 * Estos endpoints deben ser llamados SOLO desde el proxy después de que la cátedra confirme la venta.
 */
@RestController
@RequestMapping
public class VentasController {

    private static final Logger log = LoggerFactory.getLogger(VentasController.class);

    private final VentaService ventaService;

    public VentasController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    @PostMapping("/api/endpoints/v1/realizar-ventas")
    public ResponseEntity<?> guardarVentaMultiple(@RequestBody Map<String, Object> request) {
        try {
            log.info("Recibida solicitud para guardar venta múltiple: {}", request);
            
            String usuario = extractString(request, "usuario", "sessionId", "user");
            Double total = extractDoubleOrDefault(request, 0.0, "total", "precio", "precioVenta");            LocalDateTime fechaVenta = extractFecha(request, "fechaVenta", "fecha", "datetime");
            Long eventoId = extractLong(request, "eventoId", "evento");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> asientos = (List<Map<String, Object>>) request.get("asientos");
            
            // Validaciones
            if (usuario == null || usuario.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Usuario/sessionId es requerido"));
            }
            if (total == null || total <= 0) {
                total = 0.0; // O lo que decidas
            }
            if (eventoId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "eventoId es requerido"));
            }
            if (asientos == null || asientos.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Lista de asientos es requerida"));
            }
            if (fechaVenta == null) {
                fechaVenta = LocalDateTime.now();
            }
            
            // Guardar la venta
            Venta venta = ventaService.guardarVenta(usuario, total, fechaVenta, eventoId, asientos);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", venta.getId());
            response.put("usuario", venta.getUsuario());
            response.put("total", venta.getTotal());
            response.put("fechaVenta", venta.getFechaVenta().toString());
            response.put("eventoId", venta.getEventoId());
            response.put("cantidadAsientos", venta.getAsientos().size());
            response.put("result", "venta_guardada");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error guardando venta múltiple: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno: " + e.getMessage()));
        }
    }


    @PostMapping("/api/endpoints/v1/realizar-venta")
    public ResponseEntity<?> guardarVentaIndividual(@RequestBody Map<String, Object> request) {
        try {
            log.info("Recibida solicitud para guardar venta individual: {}", request);
            
            // Extraer datos de la venta
            String usuario = extractString(request, "usuario", "sessionId", "user", "owner");
            Double total = extractDoubleOrDefault(request, 0.0, "precio", "precioVenta");;
            LocalDateTime fechaVenta = extractFecha(request, "fechaVenta", "fecha", "datetime");
            Long eventoId = extractLong(request, "eventoId", "evento");
            
            // Para venta individual, crear un asiento desde seatId o fila/columna
            Map<String, Object> asientoData = new HashMap<>();
            
            // Intentar extraer fila y columna directamente
            Integer fila = extractInteger(request, "fila");
            Integer columna = extractInteger(request, "columna");
            
            // Si no están, intentar parsear desde seatId (formato: r{fila}c{columna})
            if (fila == null || columna == null) {
                String seatId = extractString(request, "seatId", "asiento");
                if (seatId != null) {
                    Map<String, Integer> parsed = parseSeatId(seatId);
                    if (parsed != null) {
                        fila = parsed.get("fila");
                        columna = parsed.get("columna");
                    }
                }
            }
            
            if (fila == null || columna == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "No se pudo determinar fila y columna del asiento"));
            }
            
            asientoData.put("fila", fila);
            asientoData.put("columna", columna);
            asientoData.put("precio", total != null ? total : extractDoubleOrDefault(request, 0.0, "precio", "precioVenta"));
            
            List<Map<String, Object>> asientos = List.of(asientoData);
            
            // Validaciones
            if (usuario == null || usuario.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Usuario/sessionId es requerido"));
            }
            if (total == null || total <= 0) {
                total = extractDoubleOrDefault(request, 0.0, "precio", "precioVenta");;
                if (total == null || total <= 0) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Total/precio debe ser mayor a 0"));
                }
            }
            if (eventoId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "eventoId es requerido"));
            }
            if (fechaVenta == null) {
                fechaVenta = LocalDateTime.now();
            }
            
            // Guardar la venta
            Venta venta = ventaService.guardarVenta(usuario, total, fechaVenta, eventoId, asientos);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", venta.getId());
            response.put("usuario", venta.getUsuario());
            response.put("total", venta.getTotal());
            response.put("fechaVenta", venta.getFechaVenta().toString());
            response.put("eventoId", venta.getEventoId());
            response.put("result", "venta_guardada");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error guardando venta individual: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno: " + e.getMessage()));
        }
    }


    @GetMapping("/api/endpoints/v1/listar-ventas")
    public ResponseEntity<?> listarVentas() {
        try {
            List<Venta> ventas = ventaService.obtenerTodasLasVentas();
            
            List<Map<String, Object>> ventasResponse = ventas.stream().map(venta -> {
                Map<String, Object> v = new HashMap<>();
                v.put("id", venta.getId());
                v.put("usuario", venta.getUsuario());
                v.put("total", venta.getTotal());
                v.put("fechaVenta", venta.getFechaVenta().toString());
                v.put("eventoId", venta.getEventoId());
                v.put("cantidadAsientos", venta.getAsientos().size());
                return v;
            }).collect(Collectors.toList());
            
            return ResponseEntity.ok(ventasResponse);
            
        } catch (Exception e) {
            log.error("Error listando ventas: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno: " + e.getMessage()));
        }
    }


    // Métodos auxiliares
    private String extractString(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    // Auxiliar para extraer double de múltiples keys
    private Double extractDouble(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null) {
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                try {
                    return Double.parseDouble(value.toString());
                } catch (NumberFormatException e) {
                    // Continuar con siguiente clave
                }
            }
        }
        return null;
    }

    // Auxiliar para extraer double con múltiples keys y default value
    private Double extractDoubleOrDefault(Map<String, Object> data, double defaultValue, String... keys) {
        Double result = extractDouble(data, keys);
        return result != null ? result : defaultValue;
    }

    private Long extractLong(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null) {
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
                try {
                    return Long.parseLong(value.toString());
                } catch (NumberFormatException e) {
                    // Continuar con siguiente clave
                }
            }
        }
        return null;
    }

    private Integer extractInteger(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime extractFecha(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null) {
                String fechaStr = value.toString();
                if (fechaStr != null && !fechaStr.trim().isEmpty()) {
                    try {
                        // Manejar fechas con Z (UTC)
                        if (fechaStr.endsWith("Z")) {
                            fechaStr = fechaStr.substring(0, fechaStr.length() - 1);
                        }
                        
                        // Intentar diferentes formatos
                        DateTimeFormatter[] formatters = {
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        };
                        
                        for (DateTimeFormatter formatter : formatters) {
                            try {
                                return LocalDateTime.parse(fechaStr, formatter);
                            } catch (Exception e) {
                                // Continuar con siguiente formato
                            }
                        }
                    } catch (Exception e) {
                        log.warn("No se pudo parsear fecha '{}': {}", fechaStr, e.getMessage());
                    }
                }
            }
        }
        return null;
    }

    private Map<String, Integer> parseSeatId(String seatId) {
        try {
            // Pattern para r{fila}c{columna}
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("r(\\d+)c(\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(seatId);
            
            if (matcher.matches()) {
                Map<String, Integer> result = new HashMap<>();
                result.put("fila", Integer.parseInt(matcher.group(1)));
                result.put("columna", Integer.parseInt(matcher.group(2)));
                return result;
            }
        } catch (Exception e) {
            log.warn("No se pudo parsear seatId: {}", seatId);
        }
        return null;
    }
}

