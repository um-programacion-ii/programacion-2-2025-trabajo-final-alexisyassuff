package com.cine.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuthenticationController (DEV helper) — versión rápida para pruebas locales.
 *
 * - POST /authenticate { "username":"...", "password":"..." } -> 200 + {"token":"..."} if valid
 * - Returns 401 if credentials invalid.
 *
 * NOTA: esta implementación usa un map in-memory para usuarios de prueba.
 * Para producción debés reemplazar la validación por una llamada a tu UserService / AuthenticationManager.
 */
@RestController
public class AuthenticationController {

    // Usuarios de prueba (username -> password). Cambialos por los reales o por una llamada a tu servicio.
    private static final Map<String,String> USERS = new ConcurrentHashMap<>();

    static {
        // ejemplo: agregá aquí los usuarios de prueba que quieras usar
        USERS.put("alu_1764429639", "secreto"); // tu usuario de prueba
        // add more as needed
    }

    private final com.cine.backend.service.TokenService tokenService;

    public AuthenticationController(com.cine.backend.service.TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/authenticate")
    public ResponseEntity<?> authenticate(@RequestBody Map<String,String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            return ResponseEntity.badRequest().body("username and password required");
        }
        String expected = USERS.get(username);
        if (expected == null || !expected.equals(password)) {
            return ResponseEntity.status(401).body("Invalid credentials");
        }
        // Generar o reutilizar token: aquí reusamos token si existe en TokenService.latest for 'catedra'
        var maybe = tokenService.getLatestToken("catedra");
        if (maybe.isPresent()) {
            return ResponseEntity.ok(Map.of("token", maybe.get().getToken()));
        }
        // Si no hay token guardado, devolvemos 200 con placeholder token (solo dev)
        String token = "dev-token-" + username;
        return ResponseEntity.ok(Map.of("token", token));
    }
}