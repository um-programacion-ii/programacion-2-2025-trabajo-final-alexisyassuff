package com.cine.proxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Component
@ConfigurationProperties(prefix = "catedra")
public class CatedraProperties {
    private String baseUrl;
    private String token; // initial token (JWT)
    private String refreshUrl; // optional: endpoint to refresh token
    private String refreshUser; // optional: username for refresh
    private String refreshPassword; // optional: password for refresh
    private long timeoutMs = 5000;

    // clave opcional en Redis donde se persiste el token (por defecto "catedra:token")
    private String redisTokenKey;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getRefreshUrl() { return refreshUrl; }
    public void setRefreshUrl(String refreshUrl) { this.refreshUrl = refreshUrl; }

    public String getRefreshUser() { return refreshUser; }
    public void setRefreshUser(String refreshUser) { this.refreshUser = refreshUser; }

    public String getRefreshPassword() { return refreshPassword; }
    public void setRefreshPassword(String refreshPassword) { this.refreshPassword = refreshPassword; }

    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }

    public String getRedisTokenKey() { return redisTokenKey; }
    public void setRedisTokenKey(String redisTokenKey) { this.redisTokenKey = redisTokenKey; }
}