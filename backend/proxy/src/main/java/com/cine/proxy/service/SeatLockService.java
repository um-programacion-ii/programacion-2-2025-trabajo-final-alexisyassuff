package com.cine.proxy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;



@Service
public class SeatLockService {
    private static final Logger log = LoggerFactory.getLogger(SeatLockService.class);

    private static final long TTL_SECONDS = 5 * 60; // 5 minutos
    private final ConcurrentHashMap<String, LockInfo> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> sold = new ConcurrentHashMap<>();

    @Autowired
    private RedisSeatService redisSeatService;   

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

    // 0) Si está vendido, no se puede bloquear
    if (sold.containsKey(k)) {
        return new BlockResult(BlockResultType.SOLD, null);
    }

    // 1) Intentar persistir en Redis primero
    try {
        boolean persisted = false;
        try {
            // usa el método de RedisSeatService que ya tenés (ajusta el nombre si varía)
            persisted = redisSeatService.tryBlockSeatForPurchase(String.valueOf(eventoId), seatId, sessionId);
        } catch (NoSuchMethodError | NoClassDefFoundError nsme) {
            // por si el método no existe (defensivo), dejamos persisted=false para fallback
            log.warn("RedisSeatService method not present: {}", nsme.getMessage());
            persisted = false;
        }

        if (persisted) {
            // Si Redis confirmó bloqueo, devolvemos SUCCESS (sessionId como holder)
            return new BlockResult(BlockResultType.SUCCESS, sessionId);
        } else {
            // Redis devolvió false => ya existe lock por otro (o no pudo setear)
            return new BlockResult(BlockResultType.LOCKED_BY_OTHER, null);
        }
    } catch (Exception redisEx) {
        // Si falla la comunicación con Redis: fallback a la lógica en memoria (tu código actual)
        log.warn("Redis write failed, falling back to in-memory lock for {}:{} -> {}", eventoId, seatId, redisEx.getMessage());

        // --- fallback: tu lógica original in-memory ---
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