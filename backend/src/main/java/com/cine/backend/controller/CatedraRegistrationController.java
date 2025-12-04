package com.cine.backend.controller;

import com.cine.backend.catedra.TokenResponse;
import com.cine.backend.model.ExternalToken;
import com.cine.backend.service.CatedraClient;
import com.cine.backend.service.TokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Endpoint para registrarse en la cátedra y guardar token en memoria (Etapa 1).
 * POST /catedra/register  body -> JSON con los campos que requiere /api/v1/agregar_usuario
 */
@RestController
@RequestMapping("/catedra")
public class CatedraRegistrationController {

    private final CatedraClient catedraClient;
    private final TokenService tokenService;

    public CatedraRegistrationController(CatedraClient catedraClient, TokenService tokenService) {
        this.catedraClient = catedraClient;
        this.tokenService = tokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> payload) {
        try {
            TokenResponse resp = catedraClient.registerUser(payload);
            if (resp == null || resp.getToken() == null) {
                return ResponseEntity.status(502).body("Cátedra no devolvió token");
            }

            // Determinar expiresAt:
            Instant expiresAt = null;

            // 1) Si la API devolvió expiresAt en seconds:
            if (resp.getExpiresAt() != null && resp.getExpiresAt() > 0) {
                expiresAt = Instant.ofEpochSecond(resp.getExpiresAt());
            } else {
                // 2) Intentar extraer claim "exp" del JWT (si es JWT)
                try {
                    String[] parts = resp.getToken().split("\\.");
                    if (parts.length >= 2) {
                        String payloadPart = parts[1];
                        byte[] decoded = Base64.getUrlDecoder().decode(payloadPart);
                        String json = new String(decoded);
                        int idx = json.indexOf("\"exp\"");
                        if (idx >= 0) {
                            int colon = json.indexOf(':', idx);
                            if (colon > 0) {
                                int start = colon + 1;
                                int end = start;
                                while (end < json.length() && (Character.isDigit(json.charAt(end)))) end++;
                                String num = json.substring(start, end).trim();
                                if (!num.isEmpty()) {
                                    try {
                                        long exp = Long.parseLong(num);
                                        expiresAt = Instant.ofEpochSecond(exp);
                                    } catch (NumberFormatException ignored) {}
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            ExternalToken saved = tokenService.saveToken(resp.getToken(), "catedra", expiresAt);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.status(502).body("Error llamando a la cátedra: " + e.getMessage());
        }
    }
}