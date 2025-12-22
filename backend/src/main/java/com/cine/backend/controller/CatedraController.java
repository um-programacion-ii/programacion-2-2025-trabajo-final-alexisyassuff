package com.cine.backend.controller;

import com.cine.backend.catedra.TokenResponse;
import com.cine.backend.model.ExternalToken;
import com.cine.backend.service.CatedraClient;
import com.cine.backend.service.TokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.temporal.ChronoUnit;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;


@RestController
@RequestMapping("/catedra")
public class CatedraController {

    private final CatedraClient catedraClient; 
    private final TokenService tokenService;  

    public CatedraController(CatedraClient catedraClient, TokenService tokenService) {
        this.catedraClient = catedraClient;
        this.tokenService = tokenService;
    }


    private Instant determineTokenExpiry(TokenResponse resp) {
        Instant expiresAt = null;

        if (resp.getExpiresAt() != null && resp.getExpiresAt() > 0) {
            expiresAt = Instant.ofEpochSecond(resp.getExpiresAt());
        } else {
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
        return expiresAt;
    }


    @GetMapping("/health")
    public ResponseEntity<String> health() {
        String result = catedraClient.pingBaseUrl();
        if (result != null && result.startsWith("OK")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(502).body(result == null ? "ERROR" : result);
        }
    }
}
