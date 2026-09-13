package com.smartfridge.dto;

import java.time.LocalDateTime;

import com.smartfridge.model.Producto;

/**
 * Representación pública de una unidad de inventario. Aplana la
 * relación con Producto (rfidTag + nombre + nutriScore) en vez de
 * anidar el objeto Producto completo: el cliente casi nunca necesita
 * el plazo de caducidad del catálogo cuando está pintando "qué hay
 * ahora mismo en la nevera", pero sí el nutriScore (se muestra en la
 * tabla del Dashboard de la app Android).
 */
public record InventarioResponse(
        Long id,
        String rfidTag,
        String nombreProducto,
        Producto.NutriScore nutriScore,
        LocalDateTime fechaEntrada,
        LocalDateTime fechaCaducidad
) {
}
