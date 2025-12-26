package com.cine.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ventas_asientos")
public class VentaAsiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Column(nullable = false)
    private Long eventoId;

    @Column(nullable = false)
    private Integer fila;

    @Column(nullable = false)
    private Integer columna;

    @Column(nullable = false)
    private Double precio;

    @Column(nullable = false)
    private Instant createdAt;

    // Constructor por defecto requerido por JPA
    public VentaAsiento() {
        this.createdAt = Instant.now();
    }

    // Constructor con parámetros principales
    public VentaAsiento(Venta venta, Long eventoId, Integer fila, Integer columna, Double precio) {
        this.venta = venta;
        this.eventoId = eventoId;
        this.fila = fila;
        this.columna = columna;
        this.precio = precio;
        this.createdAt = Instant.now();
    }

    // Getters y Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Venta getVenta() {
        return venta;
    }

    public void setVenta(Venta venta) {
        this.venta = venta;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public void setEventoId(Long eventoId) {
        this.eventoId = eventoId;
    }

    public Integer getFila() {
        return fila;
    }

    public void setFila(Integer fila) {
        this.fila = fila;
    }

    public Integer getColumna() {
        return columna;
    }

    public void setColumna(Integer columna) {
        this.columna = columna;
    }

    public Double getPrecio() {
        return precio;
    }

    public void setPrecio(Double precio) {
        this.precio = precio;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

