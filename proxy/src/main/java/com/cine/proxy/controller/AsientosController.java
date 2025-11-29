package com.cine.proxy.controller;

import com.cine.proxy.model.Seat;
import com.cine.proxy.service.RedisSeatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/asientos")
public class AsientosController {

    private final RedisSeatService seatService;

    public AsientosController(RedisSeatService seatService) {
        this.seatService = seatService;
    }

    @GetMapping("/{eventoId}")
    public ResponseEntity<List<Seat>> getAsientos(@PathVariable String eventoId) {
        List<Seat> seats = seatService.getSeatsForEvento(eventoId);
        return ResponseEntity.ok(seats);
    }
}