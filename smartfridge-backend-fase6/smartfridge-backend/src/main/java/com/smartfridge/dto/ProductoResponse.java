package com.smartfridge.dto;

import com.smartfridge.model.Producto;

/** Representación pública de un Producto, sin exponer la entidad JPA. */
public record ProductoResponse(
        String rfidTag,
        String nombre,
        Producto.NutriScore nutriScore,
        Integer plazoCaducidadDias
) {
}
