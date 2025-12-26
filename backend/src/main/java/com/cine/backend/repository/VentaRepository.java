package com.cine.backend.repository;

import com.cine.backend.model.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Long> {
    
    Optional<Venta> findById(Long id);
    
    List<Venta> findAllByOrderByFechaVentaDesc();
    
    List<Venta> findByEventoId(Long eventoId);
    
    List<Venta> findByUsuario(String usuario);
}

