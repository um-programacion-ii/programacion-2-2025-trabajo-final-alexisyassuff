package com.cine.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoints administrativos para inspeccionar y operar la cola persistente de reintentos
 * Key usada: "backend:webhook:retry"
 */
@RestController
@RequestMapping("/internal/admin/retry")
public class RetryAdminController {

    private static final String RETRY_LIST_KEY = "backend:webhook:retry";

    private final StringRedisTemplate redis;

    @Autowired
    public RetryAdminController(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @GetMapping("/size")
    public ResponseEntity<Map<String, Object>> size() {
        Long len = redis.opsForList().size(RETRY_LIST_KEY);
        Map<String, Object> resp = new HashMap<>();
        resp.put("key", RETRY_LIST_KEY);
        resp.put("size", len == null ? 0 : len);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/peek")
    public ResponseEntity<Map<String, Object>> peek(@RequestParam(name = "n", defaultValue = "10") int n) {
        if (n < 1) n = 1;
        List<String> items = redis.opsForList().range(RETRY_LIST_KEY, 0, n - 1);
        Map<String, Object> resp = new HashMap<>();
        resp.put("key", RETRY_LIST_KEY);
        resp.put("count", items == null ? 0 : items.size());
        resp.put("items", items);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/requeue")
    public ResponseEntity<Map<String, Object>> requeue(@RequestBody String payload) {
        redis.opsForList().leftPush(RETRY_LIST_KEY, payload);
        Map<String, Object> resp = new HashMap<>();
        resp.put("result", "enqueued");
        resp.put("payload", payload);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/remove")
    public ResponseEntity<Map<String, Object>> remove(@RequestBody String payload) {
        // LREM count=0 elimina todas las ocurrencias
        Long removed = redis.opsForList().remove(RETRY_LIST_KEY, 0, payload);
        Map<String, Object> resp = new HashMap<>();
        resp.put("removed", removed == null ? 0 : removed);
        resp.put("payload", payload);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/purge")
    public ResponseEntity<Map<String, Object>> purge() {
        Boolean deleted = redis.delete(RETRY_LIST_KEY);
        Map<String, Object> resp = new HashMap<>();
        resp.put("deleted", deleted == null ? false : deleted);
        resp.put("key", RETRY_LIST_KEY);
        return ResponseEntity.ok(resp);
    }
}