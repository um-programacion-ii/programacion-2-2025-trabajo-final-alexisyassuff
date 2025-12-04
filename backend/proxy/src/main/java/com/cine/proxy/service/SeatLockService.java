package com.cine.proxy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Servicio simple en memoria para locks y estado "sold".
 * - key: "<eventoId>:<seatId>"
 * TTL bloqueo: 5 minutos.
 * Cleaner periódico elimina locks expirados.
 */
@Service
public class SeatLockService {
    private static final Logger log = LoggerFactory.getLogger(SeatLockService.class);

    private static final long TTL_SECONDS = 5 * 60; // 5 minutos
    private final ConcurrentHashMap<String, LockInfo> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> sold = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void startCleaner() {
        cleaner.scheduleAtFixedRate(this::cleanupExpired, 30, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stopCleaner() {
        cleaner.shutdownNow();
    }

    private void cleanupExpired() {
        long now = Instant.now().getEpochSecond();
        try {
            for (Map.Entry<String, LockInfo> e : locks.entrySet()) {
                if (e.getValue().expireAt <= now) {
                    locks.remove(e.getKey(), e.getValue());
                    log.debug("Lock expired and removed: {}", e.getKey());
                }
            }
        } catch (Exception ex) {
            log.warn("Error during lock cleanup: {}", ex.getMessage(), ex);
        }
    }

    private String key(int eventoId, String seatId) {
        return eventoId + ":" + Objects.requireNonNull(seatId);
    }

    public enum BlockResultType { SUCCESS, ALREADY_LOCKED_BY_ME, LOCKED_BY_OTHER, SOLD }

    public static class BlockResult {
        public final BlockResultType type;
        public final String ownerSessionId; // si LOCKED_BY_OTHER, quién lo tiene; si ALREADY_LOCKED_BY_ME devuelve my sessionId

        public BlockResult(BlockResultType t, String owner) {
            this.type = t;
            this.ownerSessionId = owner;
        }
    }

    public synchronized BlockResult tryBlock(int eventoId, String seatId, String sessionId) {
        String k = key(eventoId, seatId);
        if (sold.containsKey(k)) {
            return new BlockResult(BlockResultType.SOLD, null);
        }
        LockInfo cur = locks.get(k);
        long now = Instant.now().getEpochSecond();
        if (cur == null || cur.expireAt <= now) {
            long expire = now + TTL_SECONDS;
            locks.put(k, new LockInfo(sessionId, expire));
            return new BlockResult(BlockResultType.SUCCESS, sessionId);
        } else {
            if (cur.sessionId.equals(sessionId)) {
                // refresh TTL
                cur.expireAt = now + TTL_SECONDS;
                locks.put(k, cur);
                return new BlockResult(BlockResultType.ALREADY_LOCKED_BY_ME, cur.sessionId);
            } else {
                return new BlockResult(BlockResultType.LOCKED_BY_OTHER, cur.sessionId);
            }
        }
    }

    public synchronized boolean unlockIfOwner(int eventoId, String seatId, String sessionId) {
        String k = key(eventoId, seatId);
        LockInfo cur = locks.get(k);
        if (cur == null) return false;
        if (!cur.sessionId.equals(sessionId)) return false;
        locks.remove(k);
        return true;
    }

    public enum PurchaseResultType { SUCCESS, SOLD, LOCKED_BY_OTHER }

    public static class PurchaseResult {
        public final PurchaseResultType type;

        public PurchaseResult(PurchaseResultType t) {
            this.type = t;
        }
    }

    /**
     * Compra: succeeds if either:
     *  - seat libre -> marcar sold
     *  - seat locked por misma session -> vender y remover lock
     * otherwise fail.
     */
    public synchronized PurchaseResult purchase(int eventoId, String seatId, String sessionId) {
        String k = key(eventoId, seatId);
        if (sold.containsKey(k)) {
            return new PurchaseResult(PurchaseResultType.SOLD);
        }
        LockInfo cur = locks.get(k);
        long now = Instant.now().getEpochSecond();
        if (cur == null || cur.expireAt <= now) {
            sold.put(k, Boolean.TRUE);
            locks.remove(k);
            return new PurchaseResult(PurchaseResultType.SUCCESS);
        } else {
            if (cur.sessionId.equals(sessionId)) {
                sold.put(k, Boolean.TRUE);
                locks.remove(k);
                return new PurchaseResult(PurchaseResultType.SUCCESS);
            } else {
                return new PurchaseResult(PurchaseResultType.LOCKED_BY_OTHER);
            }
        }
    }

    public synchronized String getLockOwner(int eventoId, String seatId) {
        String k = key(eventoId, seatId);
        LockInfo cur = locks.get(k);
        if (cur == null) return null;
        long now = Instant.now().getEpochSecond();
        if (cur.expireAt <= now) {
            locks.remove(k);
            return null;
        }
        return cur.sessionId;
    }

    public synchronized boolean isSold(int eventoId, String seatId) {
        String k = key(eventoId, seatId);
        return sold.containsKey(k);
    }

    public synchronized Set<String> listLockedKeys() {
        return locks.keySet();
    }

    // holder
    private static class LockInfo {
        final String sessionId;
        volatile long expireAt; // epoch seconds

        LockInfo(String sessionId, long expireAt) {
            this.sessionId = sessionId;
            this.expireAt = expireAt;
        }
    }
}