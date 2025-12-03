package com.cine.backend.repository;

import com.cine.backend.model.ExternalToken;
import java.util.Optional;

public interface TokenRepository {
    ExternalToken save(ExternalToken token);
    Optional<ExternalToken> findTopByServiceNameOrderByIdDesc(String serviceName);
}