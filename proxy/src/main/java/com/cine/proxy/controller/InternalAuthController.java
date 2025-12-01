package com.cine.proxy.controller;

import com.cine.proxy.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints internos para gestionar token de la cátedra en desarrollo.
 * - POST /internal/catedra/token  { "token": "..." }  => guarda y persiste
 * - GET  /internal/catedra/token  => devuelve token en memoria (si existe)
 * - DELETE /internal/catedra/token => borra token en memoria y Redis
 *
 * NOTA: este controlador es de utilidad para desarrollo. En producción hay que protegerlo o eliminarlo.
 */
@RestController
@RequestMapping("/internal/catedra")
public class InternalAuthController {

    private static final Logger log = LoggerFactory.getLogger(InternalAuthController.class);
    private final TokenService tokenService;

    public InternalAuthController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/token")
    public ResponseEntity<?> setToken(@RequestBody java.util.Map<String,String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body("token required");
        }
        tokenService.setTokenAndPersist(token);
        log.info("Token saved via internal endpoint (dev)");
        return ResponseEntity.ok(java.util.Map.of("saved", true));
    }

    @GetMapping("/token")
    public ResponseEntity<?> getToken() {
        String t = tokenService.getToken();
        if (t == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(java.util.Map.of("token", t));
    }

    @DeleteMapping("/token")
    public ResponseEntity<?> deleteToken() {
        tokenService.clearToken();
        return ResponseEntity.ok(java.util.Map.of("deleted", true));
    }
}