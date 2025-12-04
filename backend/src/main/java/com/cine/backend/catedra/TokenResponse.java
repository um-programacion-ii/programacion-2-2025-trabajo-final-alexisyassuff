package com.cine.backend.catedra;

/**
 * DTO simple que representa la respuesta de /api/v1/agregar_usuario
 * Ajustá campos si la cátedra devuelve nombre de campo distinto.
 */
public class TokenResponse {
    private String token;
    private Long expiresAt; // optional: epoch seconds, si la cátedra lo devuelve

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Long expiresAt) { this.expiresAt = expiresAt; }
}