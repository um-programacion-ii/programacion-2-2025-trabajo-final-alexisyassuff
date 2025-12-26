package com.cine.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ventas")
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String usuario; 

    @Column(nullable = false)
    private Double total;

    @Column(nullable = false)
    private LocalDateTime fechaVenta;

    @Column(nullable = false)
    private Long eventoId;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VentaAsiento> asientos = new ArrayList<>();

    public Venta() {
        this.createdAt = Instant.now();
    }

    public Venta(String usuario, Double total, LocalDateTime fechaVenta, Long eventoId) {
        this.usuario = usuario;
        this.total = total;
        this.fechaVenta = fechaVenta;
        this.eventoId = eventoId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public Double getTotal() {
        return total;
    }

    public void setTotal(Double total) {
        this.total = total;
    }

    public LocalDateTime getFechaVenta() {
        return fechaVenta;
    }

    public void setFechaVenta(LocalDateTime fechaVenta) {
        this.fechaVenta = fechaVenta;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public void setEventoId(Long eventoId) {
        this.eventoId = eventoId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<VentaAsiento> getAsientos() {
        return asientos;
    }

    public void setAsientos(List<VentaAsiento> asientos) {
        this.asientos = asientos;
    }

    public void addAsiento(VentaAsiento asiento) {
        asientos.add(asiento);
        asiento.setVenta(this);
    }
}

