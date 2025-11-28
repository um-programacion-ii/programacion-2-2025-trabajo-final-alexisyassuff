package com.cine.backend.repository;

import com.cine.backend.model.ExternalToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TokenRepository extends JpaRepository<ExternalToken, Long> {
    Optional<ExternalToken> findTopByServiceNameOrderByIdDesc(String serviceName);
}