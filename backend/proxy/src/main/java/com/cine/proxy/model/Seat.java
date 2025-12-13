package com.cine.proxy.model;

import java.time.Instant;

public class Seat {
    private String seatId;
    private String status; // e.g. "LIBRE", "BLOQUEADO", "VENDIDO"
    private String holder; // sessionId del usuario
    private Instant updatedAt;
    
    // Nuevos campos para datos del comprador
    private Comprador comprador;
    private String fechaVenta;

    public Seat() {}

    public Seat(String seatId, String status, String holder, Instant updatedAt) {
        this.seatId = seatId;
        this.status = status;
        this.holder = holder;
        this.updatedAt = updatedAt;
    }

    public String getSeatId() { return seatId; }
    public void setSeatId(String seatId) { this.seatId = seatId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getHolder() { return holder; }
    public void setHolder(String holder) { this.holder = holder; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Comprador getComprador() { return comprador; }
    public void setComprador(Comprador comprador) { this.comprador = comprador; }

    public String getFechaVenta() { return fechaVenta; }
    public void setFechaVenta(String fechaVenta) { this.fechaVenta = fechaVenta; }
    
    // Clase interna para datos del comprador
    public static class Comprador {
        private String persona;
        
        public Comprador() {}
        
        public Comprador(String persona) {
            this.persona = persona;
        }
        
        public String getPersona() { return persona; }
        public void setPersona(String persona) { this.persona = persona; }
    }
}