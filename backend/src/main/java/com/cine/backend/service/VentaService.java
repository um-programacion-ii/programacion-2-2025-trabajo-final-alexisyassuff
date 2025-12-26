package com.cine.backend.service;

import com.cine.backend.model.Venta;
import com.cine.backend.model.VentaAsiento;
import com.cine.backend.repository.VentaRepository;
import com.cine.backend.repository.VentaAsientoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class VentaService {

    private static final Logger log = LoggerFactory.getLogger(VentaService.class);

    private final VentaRepository ventaRepository;
    private final VentaAsientoRepository ventaAsientoRepository;

    public VentaService(VentaRepository ventaRepository, 
                       VentaAsientoRepository ventaAsientoRepository) {
        this.ventaRepository = ventaRepository;
        this.ventaAsientoRepository = ventaAsientoRepository;
    }

    /**
     * Guarda una venta confirmada con sus asientos.
     * Este método debe ser llamado solo después de que la cátedra confirme la venta.
     */
    @Transactional
    public Venta guardarVenta(String usuario, Double total, LocalDateTime fechaVenta, 
                             Long eventoId, List<Map<String, Object>> asientos) {
        log.info("Guardando venta: usuario={}, total={}, eventoId={}, asientos={}", 
                usuario, total, eventoId, asientos.size());
        
        // Crear la venta
        Venta venta = new Venta(usuario, total, fechaVenta, eventoId);
        
        // Agregar los asientos
        for (Map<String, Object> asientoData : asientos) {
            Integer fila = extractInteger(asientoData, "fila");
            Integer columna = extractInteger(asientoData, "columna");
            Double precio = extractDouble(asientoData, "precio", "precioVenta");
            
            if (fila != null && columna != null && precio != null) {
                VentaAsiento ventaAsiento = new VentaAsiento(venta, eventoId, fila, columna, precio);
                venta.addAsiento(ventaAsiento);
            } else {
                log.warn("Asiento con datos incompletos ignorado: {}", asientoData);
            }
        }
        
        // Guardar la venta (los asientos se guardan en cascada)
        Venta saved = ventaRepository.save(venta);
        log.info("Venta guardada exitosamente con ID: {}", saved.getId());
        
        return saved;
    }

    /**
     * Obtiene todas las ventas ordenadas por fecha descendente.
     */
    public List<Venta> obtenerTodasLasVentas() {
        return ventaRepository.findAllByOrderByFechaVentaDesc();
    }

    /**
     * Obtiene una venta por ID.
     */
    public Optional<Venta> obtenerVentaPorId(Long id) {
        return ventaRepository.findById(id);
    }

    /**
     * Obtiene los asientos de una venta específica.
     */
    public List<VentaAsiento> obtenerAsientosDeVenta(Long ventaId) {
        return ventaAsientoRepository.findByVentaId(ventaId);
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
}

