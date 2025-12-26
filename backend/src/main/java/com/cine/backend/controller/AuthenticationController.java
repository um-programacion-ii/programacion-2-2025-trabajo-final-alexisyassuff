package com.cine.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
// import com.cine.backend.service.TokenService;
import com.cine.backend.model.ExternalToken;
import com.cine.backend.catedra.TokenResponse;
import java.util.Optional;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;


@RestController
public class AuthenticationController {
    private static final Map<String, String> USERS = new ConcurrentHashMap<>();
    private static final Map<String, SessionInfo> SESSIONS = new ConcurrentHashMap<>();
    static {
        USERS.put("alu_1764429639", "secreto");
    }

    static class SessionInfo {
        String username;
        String token;
        Instant expiresAt;
    }

    @PostMapping("/authenticate")
    public ResponseEntity<?> authenticate(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            return ResponseEntity.badRequest().body("username and password required");
        }
        String expected = USERS.get(username);
        if (expected == null || !expected.equals(password)) {
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(15 * 60);

        SessionInfo session = new SessionInfo();
        session.username = username;
        session.token = token;
        session.expiresAt = expiresAt;
        SESSIONS.put(token, session);

        return ResponseEntity.ok(Map.of("token", token, "expiresAt", expiresAt.toString()));
    }

    @PostMapping("/validate-token")
    public ResponseEntity<?> validateToken(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        boolean valid = isTokenValid(token);
        return ResponseEntity.ok(Map.of("valid", valid));
    }

    private static boolean isTokenValid(String token) {
        SessionInfo s = SESSIONS.get(token);
        return s != null && s.expiresAt.isAfter(Instant.now());
    }
}