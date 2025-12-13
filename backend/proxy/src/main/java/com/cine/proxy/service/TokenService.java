package com.cine.proxy.service;

import com.cine.proxy.config.CatedraProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final ObjectMapper mapper = new ObjectMapper();

    private final String redisKey;

    public TokenService(CatedraProperties props, WebClient.Builder webClientBuilder, StringRedisTemplate redis) {
        this.props = props;
        this.webClientBuilder = webClientBuilder;
        this.redis = redis;
        this.redisKey = props.getRedisTokenKey() == null || props.getRedisTokenKey().isBlank()
                ? "catedra:token" : props.getRedisTokenKey();

        // try to load from Redis first
        String redisToken = null;
        try {
            redisToken = redis.opsForValue().get(this.redisKey);
            if (redisToken != null && !redisToken.isBlank()) {
                tokenRef.set(redisToken);
                log.info("Loaded cátedra token from Redis key={}", this.redisKey);
            }
        } catch (Exception e) {
            log.warn("Could not read token from Redis (key={}): {}", this.redisKey, e.getMessage());
        }

        // if no token in Redis, try to bootstrap from properties and persist it
        if (redisToken == null || redisToken.isBlank()) {
            if (props.getToken() != null && !props.getToken().isBlank()) {
                log.info("No token in Redis, using token from application.yml and persisting it");
                setTokenAndPersist(props.getToken());
            }
        }
    }

    /** Devuelve el token actual en memoria (puede ser null) */
    public String getToken() {
        String token = tokenRef.get();
        
        // Verificar si el token existe en Redis (si no existe, significa que expiró)
        if (token != null) {
            try {
                String redisToken = redis.opsForValue().get(this.redisKey);
                if (redisToken == null || !redisToken.equals(token)) {
                    log.info("Token expired or not found in Redis, clearing memory token");
                    tokenRef.set(null);
                    return null;
                }
            } catch (Exception e) {
                log.warn("Error checking token in Redis: {}", e.getMessage());
                // En caso de error de Redis, mantener el token en memoria
            }
        }
        
        return token;
    }
    
    /** Verifica si hay un token válido disponible */
    public boolean hasValidToken() {
        return getToken() != null;
    }

    /** Setea el token en memoria y lo persiste en Redis con TTL máximo de 1 hora */
    public void setTokenAndPersist(String token) {
        tokenRef.set(token);
        try {
            // Intentar extraer la expiración del JWT para establecer TTL
            Duration jwtTtl = extractJwtTtl(token);
            
            // TTL máximo de 1 hora (3600 segundos)
            Duration maxTtl = Duration.ofHours(1);
            Duration finalTtl;
            
            if (jwtTtl != null && jwtTtl.getSeconds() > 0) {
                // Usar el menor entre el JWT TTL y 1 hora
                finalTtl = jwtTtl.getSeconds() < maxTtl.getSeconds() ? jwtTtl : maxTtl;
                log.info("JWT TTL: {} seconds, Using: {} seconds (max 1h)", jwtTtl.getSeconds(), finalTtl.getSeconds());
            } else {
                // Usar TTL por defecto de 1 hora si no se puede extraer
                finalTtl = maxTtl;
                log.info("Couldn't extract JWT TTL, using default: {} seconds", finalTtl.getSeconds());
            }
            
            // Guardar con el TTL calculado
            redis.opsForValue().set(this.redisKey, token, finalTtl);
            log.info("Persisted cátedra token into Redis key={} with TTL={} seconds", this.redisKey, finalTtl.getSeconds());
            
            // Verificar que se guardó correctamente
            String stored = redis.opsForValue().get(this.redisKey);
            if (stored != null && stored.equals(token)) {
                log.info("Token verification: Successfully stored and retrieved");
            } else {
                log.error("Token verification: FAILED - stored token doesn't match");
            }
            
        } catch (Exception e) {
            log.error("Failed to persist token into Redis: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Extrae el TTL de un JWT decodificando su payload
     */
    private Duration extractJwtTtl(String jwt) {
        try {
            if (jwt == null || jwt.isBlank()) {
                return null;
            }
            
            // Split JWT (header.payload.signature)
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            
            // Decodificar payload (parte 2)
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = mapper.readTree(payloadBytes);
            
            // Extraer 'exp' (expiration time en segundos Unix)
            JsonNode expNode = payload.get("exp");
            if (expNode == null || !expNode.isNumber()) {
                return null;
            }
            
            long expSeconds = expNode.asLong();
            long nowSeconds = Instant.now().getEpochSecond();
            long ttlSeconds = expSeconds - nowSeconds;
            
            if (ttlSeconds <= 0) {
                log.warn("JWT ya expirado: exp={}, now={}", expSeconds, nowSeconds);
                return Duration.ofSeconds(1); // TTL mínimo para que expire inmediatamente
            }
            
            return Duration.ofSeconds(ttlSeconds);
            
        } catch (Exception e) {
            log.warn("No se pudo extraer TTL del JWT: {}", e.getMessage());
            return null;
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