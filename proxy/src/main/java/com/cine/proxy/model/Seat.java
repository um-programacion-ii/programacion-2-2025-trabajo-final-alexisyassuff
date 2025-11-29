package com.cine.proxy.model;

import java.time.Instant;

public class Seat {
    private String seatId;
    private String status; // e.g. "LIBRE", "BLOQUEADO", "OCUPADO"
    private String holder; // usuario
    private Instant updatedAt;

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
}