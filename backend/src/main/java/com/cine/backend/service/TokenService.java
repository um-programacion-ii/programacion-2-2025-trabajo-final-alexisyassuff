package com.cine.backend.service;

import com.cine.backend.model.ExternalToken;
import com.cine.backend.repository.TokenRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class TokenService {

    private final TokenRepository repo;

    public TokenService(TokenRepository repo) {
        this.repo = repo;
    }

    /**
     * Guarda token con fecha de emisión y expiración opcional.
     */
    public ExternalToken saveToken(String token, String serviceName, Instant expiresAt) {
        ExternalToken t = new ExternalToken();
        t.setToken(token);
        t.setIssuedAt(Instant.now());
        t.setExpiresAt(expiresAt);
        t.setServiceName(serviceName);
        return repo.save(t);
    }

    // Compatibilidad: mantiene el método antiguo si lo usás en otros sitios
    public ExternalToken saveToken(String token, String serviceName) {
        return saveToken(token, serviceName, null);
    }

    public Optional<ExternalToken> getLatestToken(String serviceName) {
        return repo.findTopByServiceNameOrderByIdDesc(serviceName);
    }


}