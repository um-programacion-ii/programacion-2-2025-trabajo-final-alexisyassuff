package com.cine.proxy.controller;

import com.cine.proxy.service.TokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * Controlador interno para operaciones administrativas
 */
@RestController
@RequestMapping("/internal")
public class InternalController {
    
    private final TokenService tokenService;
    
    public InternalController(TokenService tokenService) {
        this.tokenService = tokenService;
    }
    
    /**
     * GET /internal/catedra/token - Obtener token actual
     */
    @GetMapping("/catedra/token")
    public ResponseEntity<?> getToken() {
        String token = tokenService.getToken();
        if (token == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("token", token));
    }
    
    /**
     * POST /internal/catedra/token - Establecer nuevo token (para testing del TTL)
     */
    @PostMapping("/catedra/token")
    public ResponseEntity<?> setToken(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token requerido"));
        }
        
        tokenService.setTokenAndPersist(token);
        return ResponseEntity.ok(Map.of("message", "Token actualizado con TTL automático"));
    }
}
