package com.cine.proxy.controller;

import com.cine.proxy.model.Seat;
import com.cine.proxy.service.RedisSeatService;
import com.cine.proxy.service.SeatLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/asientos")
public class AsientosController {
    private static final Logger log = LoggerFactory.getLogger(AsientosController.class);

    private final RedisSeatService seatService;
    private final SeatLockService seatLockService;

    public AsientosController(RedisSeatService seatService, SeatLockService seatLockService) {
        this.seatService = seatService;
        this.seatLockService = seatLockService;
    }

    /**
     * GET /asientos/{eventoId}
     * Devuelve lista de asientos enriquecida con:
     *  - status: LIBRE | BLOQUEADO | VENDIDO (según seatService + seatLockService)
     *  - holder: sessionId que bloqueó (solo si BLOQUEADO)
     *  - updatedAt: si lo provee RedisSeatService
     *
     * Retorna JSON array con objetos { seatId, status, holder, updatedAt }
     */
    @GetMapping("/{eventoId}")
    public ResponseEntity<List<Map<String,Object>>> getAsientos(@PathVariable String eventoId) {
        try {
            List<Seat> seats = seatService.getSeatsForEvento(eventoId);
            List<Map<String,Object>> out = new ArrayList<>();
            int eid;
            try {
                eid = Integer.parseInt(eventoId);
            } catch (Exception ex) {
                // fallback: si no es convertible, usamos -1 (seatLockService manejará)
                eid = -1;
            }

            for (Seat s : seats) {
                Map<String,Object> m = new HashMap<>();
                String seatId = s.getSeatId();
                m.put("seatId", seatId);

                boolean sold = seatLockService.isSold(eid, seatId);
                String owner = seatLockService.getLockOwner(eid, seatId);

                if (sold) {
                    m.put("status", "VENDIDO");
                } else if (owner != null) {
                    m.put("status", "BLOQUEADO");
                    m.put("holder", owner);
                } else {
                    // keep upstream status if provided, otherwise LIBRE
                    String upstreamStatus = s.getStatus();
                    m.put("status", upstreamStatus == null ? "LIBRE" : upstreamStatus);
                    // copy holder from upstream if present
                    if (s.getHolder() != null && !s.getHolder().isBlank()) {
                        m.put("holder", s.getHolder());
                    }
                }

                if (s.getUpdatedAt() != null) {
                    m.put("updatedAt", s.getUpdatedAt().toString());
                }

                out.add(m);
            }

            return ResponseEntity.ok(out);
        } catch (Exception ex) {
            log.error("Error assembling seats enriched list for evento {}: {}", eventoId, ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "internal");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of(err));
        }
    }

    /**
     * POST /asientos/{eventoId}/{seatId}/block
     * Header required: X-Session-Id
     */
    @PostMapping("/{eventoId}/{seatId}/block")
    public ResponseEntity<?> blockSeat(@PathVariable int eventoId,
                                       @PathVariable String seatId,
                                       @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        try {
            if (sessionId == null || sessionId.isBlank()) {
                Map<String,Object> resp = new HashMap<>();
                resp.put("error", "Missing X-Session-Id");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resp);
            }
            SeatLockService.BlockResult res = seatLockService.tryBlock(eventoId, seatId, sessionId);
            switch (res.type) {
                case SUCCESS:
                case ALREADY_LOCKED_BY_ME: {
                    Map<String,Object> ok = new HashMap<>();
                    ok.put("result", "locked");
                    ok.put("owner", res.ownerSessionId);
                    return ResponseEntity.ok(ok);
                }
                case SOLD:
                    Map<String,Object> sold = new HashMap<>();
                    sold.put("error", "SOLD");
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(sold);
                case LOCKED_BY_OTHER:
                default:
                    Map<String,Object> conflict = new HashMap<>();
                    conflict.put("error", "LOCKED_BY_OTHER");
                    conflict.put("owner", res.ownerSessionId);
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(conflict);
            }
        } catch (Exception ex) {
            log.error("Error blockSeat: {}", ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "internal");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * POST /asientos/{eventoId}/{seatId}/unlock
     * Header: X-Session-Id
     */
    @PostMapping("/{eventoId}/{seatId}/unlock")
    public ResponseEntity<?> unlockSeat(@PathVariable int eventoId,
                                        @PathVariable String seatId,
                                        @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        try {
            if (sessionId == null || sessionId.isBlank()) {
                Map<String,Object> resp = new HashMap<>();
                resp.put("error", "Missing X-Session-Id");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resp);
            }
            boolean ok = seatLockService.unlockIfOwner(eventoId, seatId, sessionId);
            if (ok) {
                Map<String,Object> r = new HashMap<>();
                r.put("result", "unlocked");
                return ResponseEntity.ok(r);
            } else {
                Map<String,Object> r = new HashMap<>();
                r.put("error", "NOT_OWNER_OR_NOT_LOCKED");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(r);
            }
        } catch (Exception ex) {
            log.error("Error unlockSeat: {}", ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "internal");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * POST /asientos/{eventoId}/{seatId}/purchase
     * Header: X-Session-Id
     */
    @PostMapping("/{eventoId}/{seatId}/purchase")
    public ResponseEntity<?> purchaseSeat(@PathVariable int eventoId,
                                          @PathVariable String seatId,
                                          @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        try {
            if (sessionId == null || sessionId.isBlank()) {
                Map<String,Object> resp = new HashMap<>();
                resp.put("error", "Missing X-Session-Id");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resp);
            }
            SeatLockService.PurchaseResult res = seatLockService.purchase(eventoId, seatId, sessionId);
            switch (res.type) {
                case SUCCESS: {
                    Map<String,Object> ok = new HashMap<>();
                    ok.put("result", "purchased");
                    return ResponseEntity.ok(ok);
                }
                case SOLD: {
                    Map<String,Object> r = new HashMap<>();
                    r.put("error", "SOLD");
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(r);
                }
                case LOCKED_BY_OTHER: {
                    Map<String,Object> r = new HashMap<>();
                    r.put("error", "LOCKED_BY_OTHER");
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(r);
                }
                default: {
                    Map<String,Object> r = new HashMap<>();
                    r.put("error", "unknown");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(r);
                }
            }
        } catch (Exception ex) {
            log.error("Error purchaseSeat: {}", ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "internal");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * GET /asientos/{eventoId}/{seatId}/state
     * Optional header X-Session-Id to indicate "by me".
     */
    @GetMapping("/{eventoId}/{seatId}/state")
    public ResponseEntity<?> seatState(@PathVariable int eventoId,
                                       @PathVariable String seatId,
                                       @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        try {
            boolean sold = seatLockService.isSold(eventoId, seatId);
            String owner = seatLockService.getLockOwner(eventoId, seatId);
            String state;
            if (sold) state = "VENDIDO";
            else if (owner == null) state = "LIBRE";
            else if (sessionId != null && sessionId.equals(owner)) state = "BLOQUEADO_POR_MI";
            else state = "BLOQUEADO_POR_OTRO";

            Map<String, Object> resp = new HashMap<>();
            resp.put("state", state);
            if (owner != null) resp.put("owner", owner);
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            log.error("Error seatState: {}", ex.getMessage(), ex);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "internal");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }
}