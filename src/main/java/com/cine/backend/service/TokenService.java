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

    public ExternalToken saveToken(String token, String serviceName) {
        ExternalToken t = new ExternalToken();
        t.setToken(token);
        t.setIssuedAt(Instant.now());
        t.setServiceName(serviceName);
        // expiración no calculada por ahora
        return repo.save(t);
    }

    public Optional<ExternalToken> getLatestToken(String serviceName) {
        return repo.findTopByServiceNameOrderByIdDesc(serviceName);
    }
}