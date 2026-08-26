package com.smartfridge.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartfridge.dto.InventarioResponse;
import com.smartfridge.model.Inventario;
import com.smartfridge.repository.InventarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Deliberadamente de solo lectura (sin POST/DELETE): las altas y bajas
 * de inventario ocurren a través del flujo MQTT (RFID + modo), no de
 * la API REST. Exponer aquí un POST/DELETE directo abriría una vía
 * paralela para desincronizar inventario y registro sin pasar por
 * InventarioService.
 */
@RestController
@RequestMapping("/api/inventario")
@RequiredArgsConstructor
public class InventarioController {

    private final InventarioRepository inventarioRepository;

    @GetMapping
    public List<InventarioResponse> listar() {
        return inventarioRepository.findAll().stream()
                .map(InventarioController::toResponse)
                .toList();
    }

    private static InventarioResponse toResponse(Inventario inventario) {
        return new InventarioResponse(
                inventario.getId(),
                inventario.getProducto().getRfidTag(),
                inventario.getProducto().getNombre(),
                inventario.getProducto().getNutriScore(),
                inventario.getFechaEntrada(),
                inventario.getFechaCaducidad()
        );
    }
}
