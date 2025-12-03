package com.cine.proxy.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedisController {

    private static final Logger log = LoggerFactory.getLogger(RedisController.class);

    private final StringRedisTemplate redis;

    public RedisController(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @GetMapping("/redis/ping")
    public ResponseEntity<String> ping() {
        RedisConnection connection = null;
        try {
            connection = redis.getConnectionFactory().getConnection();
            // En esta versión ping() devuelve String
            String r = connection.ping();
            String pong = r == null ? null : r;
            log.info("Redis ping result: {}", pong);
            return ResponseEntity.ok(pong == null ? "NO_PONG" : pong);
        } catch (Exception e) {
            log.error("REDIS ping failed", e);
            return ResponseEntity.status(502).body("REDIS_ERROR: " + e.getMessage());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignored) { }
            }
        }
    }
}