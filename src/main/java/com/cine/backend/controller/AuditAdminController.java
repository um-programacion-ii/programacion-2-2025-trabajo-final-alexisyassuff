package com.cine.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoints administrativos para inspeccionar la lista de auditoría de reconciliación.
 * Key usada: "backend:reconciliation:applied"
 * También expone "backend:reconciliation:applied:latest" si está presente.
 */
@RestController
@RequestMapping("/internal/admin/reconciliation")
public class AuditAdminController {

    private static final String AUDIT_LIST_KEY = "backend:reconciliation:applied";
    private static final String AUDIT_LATEST_KEY = "backend:reconciliation:applied:latest";

    private final StringRedisTemplate redis;

    @Autowired
    public AuditAdminController(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @GetMapping("/size")
    public ResponseEntity<Map<String, Object>> size() {
        Long len = redis.opsForList().size(AUDIT_LIST_KEY);
        Map<String, Object> resp = new HashMap<>();
        resp.put("key", AUDIT_LIST_KEY);
        resp.put("size", len == null ? 0 : len);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/peek")
    public ResponseEntity<Map<String, Object>> peek(@RequestParam(name = "n", defaultValue = "10") int n) {
        if (n < 1) n = 1;
        List<String> items = redis.opsForList().range(AUDIT_LIST_KEY, 0, n - 1);
        Map<String, Object> resp = new HashMap<>();
        resp.put("key", AUDIT_LIST_KEY);
        resp.put("count", items == null ? 0 : items.size());
        resp.put("items", items);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> latest() {
        String latest = redis.opsForValue().get(AUDIT_LATEST_KEY);
        Map<String, Object> resp = new HashMap<>();
        resp.put("key", AUDIT_LATEST_KEY);
        resp.put("value", latest);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/purge")
    public ResponseEntity<Map<String, Object>> purge() {
        Boolean deleted = redis.delete(AUDIT_LIST_KEY);
        Map<String, Object> resp = new HashMap<>();
        resp.put("deleted", deleted == null ? false : deleted);
        resp.put("key", AUDIT_LIST_KEY);
        return ResponseEntity.ok(resp);
    }
}