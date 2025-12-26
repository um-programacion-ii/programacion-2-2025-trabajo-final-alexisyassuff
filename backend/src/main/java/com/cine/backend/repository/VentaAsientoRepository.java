package com.cine.backend.repository;

import com.cine.backend.model.VentaAsiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VentaAsientoRepository extends JpaRepository<VentaAsiento, Long> {
    
    List<VentaAsiento> findByVentaId(Long ventaId);
    
    List<VentaAsiento> findByEventoId(Long eventoId);
}

