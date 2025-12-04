package com.cine.proxy.service;

import com.cine.proxy.config.CatedraProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TokenService:
 * - mantiene token en memoria (tokenRef)
 * - persiste token en Redis bajo la clave configurada (por defecto "catedra:token")
 * - intenta refresh vía refreshUrl (si está configurado)
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final CatedraProperties props;
    private final WebClient.Builder webClientBuilder;
    private final StringRedisTemplate redis;
    private final AtomicReference<String> tokenRef = new AtomicReference<>();

    private final String redisKey;

    public TokenService(CatedraProperties props, WebClient.Builder webClientBuilder, StringRedisTemplate redis) {
        this.props = props;
        this.webClientBuilder = webClientBuilder;
        this.redis = redis;
        this.redisKey = props.getRedisTokenKey() == null || props.getRedisTokenKey().isBlank()
                ? "catedra:token" : props.getRedisTokenKey();

        // try to bootstrap token from properties first
        if (props.getToken() != null && !props.getToken().isBlank()) {
            tokenRef.set(props.getToken());
        }

        // then try to load from Redis (overrides property if present in Redis)
        try {
            String t = redis.opsForValue().get(this.redisKey);
            if (t != null && !t.isBlank()) {
                tokenRef.set(t);
                log.info("Loaded cátedra token from Redis key={}", this.redisKey);
            }
        } catch (Exception e) {
            log.warn("Could not read token from Redis (key={}): {}", this.redisKey, e.getMessage());
        }
    }

    /** Devuelve el token actual en memoria (puede ser null) */
    public String getToken() {
        return tokenRef.get();
    }

    /** Setea el token en memoria y lo persiste en Redis */
    public void setTokenAndPersist(String token) {
        tokenRef.set(token);
        try {
            redis.opsForValue().set(this.redisKey, token);
            log.info("Persisted cátedra token into Redis key={}", this.redisKey);
        } catch (Exception e) {
            log.warn("Failed to persist token into Redis: {}", e.getMessage());
        }
    }

    /** Borra token de memoria y Redis */
    public void clearToken() {
        tokenRef.set(null);
        try {
            redis.delete(this.redisKey);
            log.info("Deleted cátedra token from Redis key={}", this.redisKey);
        } catch (Exception e) {
            log.warn("Failed to delete token from Redis: {}", e.getMessage());
        }
    }

    /**
     * Attempts to refresh the token using configured refreshUrl and credentials.
     * Returns true if token refreshed and persisted.
     */
    public synchronized boolean refreshTokenIfPossible() {
        String refreshUrl = props.getRefreshUrl();
        if (refreshUrl == null || refreshUrl.isBlank()) {
            log.debug("No refreshUrl configured for cátedra; cannot refresh token");
            return false;
        }

        try {
            WebClient client = webClientBuilder
                    .baseUrl(refreshUrl.startsWith("http") ? "" : props.getBaseUrl())
                    .build();

            Map<String, String> body = Map.of(
                    "username", props.getRefreshUser() == null ? "" : props.getRefreshUser(),
                    "password", props.getRefreshPassword() == null ? "" : props.getRefreshPassword()
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = client.post()
                    .uri(refreshUrl.startsWith("http") ? refreshUrl : refreshUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(props.getTimeoutMs()))
                    .block();

            if (resp == null) {
                log.warn("Token refresh returned null body");
                return false;
            }

            Object maybeAccess = resp.getOrDefault("access_token", resp.get("token"));
            if (maybeAccess == null) {
                log.warn("Token refresh response didn't contain access_token/token; response={}", resp);
                return false;
            }
            String newToken = maybeAccess.toString();
            setTokenAndPersist(newToken);
            log.info("Successfully refreshed token and persisted");
            return true;
        } catch (Exception e) {
            log.warn("Failed to refresh token: {}", e.getMessage());
            return false;
        }
    }
}