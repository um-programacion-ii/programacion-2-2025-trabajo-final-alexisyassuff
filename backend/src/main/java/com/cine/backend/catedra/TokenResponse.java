package com.cine.backend.catedra;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class TokenResponse {
    private String token;
    private Long expiresAt; // optional: epoch seconds, si la cátedra lo devuelve

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Long expiresAt) { this.expiresAt = expiresAt; }

}
