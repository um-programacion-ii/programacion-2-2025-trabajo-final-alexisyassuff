package com.cine.backend.repository;

import com.cine.backend.model.ExternalToken;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementación en memoria de TokenRepository para la Etapa 1.
 * - Thread-safe lo suficiente para pruebas locales.
 * - Genera ids automáticamente.
 * - Mantiene lista por serviceName y devuelve el token con mayor id.
 */
@Repository
public class InMemoryTokenRepository implements TokenRepository {

    private final AtomicLong idGen = new AtomicLong(1);
    private final ConcurrentMap<String, List<ExternalToken>> store = new ConcurrentHashMap<>();

    @Override
    public ExternalToken save(ExternalToken token) {
        if (token == null) throw new IllegalArgumentException("token cannot be null");
        if (token.getServiceName() == null) throw new IllegalArgumentException("serviceName cannot be null");

        if (token.getId() == null) {
            token.setId(idGen.getAndIncrement());
        }
        store.compute(token.getServiceName(), (k, list) -> {
            if (list == null) {
                list = Collections.synchronizedList(new ArrayList<>());
            }
            list.add(token);
            return list;
        });
        return token;
    }

    @Override
    public Optional<ExternalToken> findTopByServiceNameOrderByIdDesc(String serviceName) {
        List<ExternalToken> list = store.get(serviceName);
        if (list == null || list.isEmpty()) return Optional.empty();

        synchronized (list) {
            return list.stream().max(Comparator.comparing(ExternalToken::getId));
        }
    }
}