package com.cine.backend.repository;

import com.cine.backend.model.Evento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventoRepository extends JpaRepository<Evento, Long> {
    
    Optional<Evento> findById(Long id);
}

