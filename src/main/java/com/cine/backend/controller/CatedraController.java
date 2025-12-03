package com.cine.backend.controller;

import com.cine.backend.service.CatedraClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatedraController {

    private final CatedraClient catedraClient;

    public CatedraController(CatedraClient catedraClient) {
        this.catedraClient = catedraClient;
    }

    @GetMapping("/catedra/health")
    public ResponseEntity<String> health() {
        String result = catedraClient.pingBaseUrl();
        if (result != null && result.startsWith("OK")) {
            return ResponseEntity.ok(result);
        } else {
            // 502 Bad Gateway: el upstream no respondió correctamente
            return ResponseEntity.status(502).body(result == null ? "ERROR" : result);
        }
    }
}