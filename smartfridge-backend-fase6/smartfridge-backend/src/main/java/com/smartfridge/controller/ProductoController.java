package com.smartfridge.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.smartfridge.dto.ProductoRequest;
import com.smartfridge.dto.ProductoResponse;
import com.smartfridge.model.Producto;
import com.smartfridge.repository.ProductoRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Sustituye a la parte de "productos" del monolítico {@code DatabaseServlet}
 * legacy: aquí solo se atiende /api/productos, con verbos HTTP propios
 * (GET para listar, POST para crear) en vez de un único doPost que
 * devolvía las 5 tablas enteras en un JSON gigante.
 */
@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoRepository productoRepository;

    @GetMapping
    public List<ProductoResponse> listar() {
        return productoRepository.findAll().stream()
                .map(ProductoController::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoResponse crear(@Valid @RequestBody ProductoRequest request) {
        Producto producto = Producto.builder()
                .rfidTag(request.rfidTag())
                .nombre(request.nombre())
                .nutriScore(request.nutriScore())
                .plazoCaducidadDias(request.plazoCaducidadDias())
                .build();

        return toResponse(productoRepository.save(producto));
    }

    private static ProductoResponse toResponse(Producto producto) {
        return new ProductoResponse(
                producto.getRfidTag(),
                producto.getNombre(),
                producto.getNutriScore(),
                producto.getPlazoCaducidadDias()
        );
    }
}
