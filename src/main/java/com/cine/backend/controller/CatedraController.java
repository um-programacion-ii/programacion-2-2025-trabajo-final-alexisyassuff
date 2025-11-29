package com.cine.backend.controller;
import com.cine.backend.service.CatedraClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/catedra")
public class CatedraController {

    private final CatedraClientService clientService;

    public CatedraController(CatedraClientService clientService) {
        this.clientService = clientService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> payload) {
        Map<String, Object> resp = clientService.register(payload);
        if (resp == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No response from cátedra"));
        }
        return ResponseEntity.ok(resp);
    }
}