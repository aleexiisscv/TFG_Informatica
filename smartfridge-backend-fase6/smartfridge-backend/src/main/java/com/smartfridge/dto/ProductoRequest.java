package com.smartfridge.dto;

import com.smartfridge.model.Producto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Datos que la app Android/web debe aportar para dar de alta un producto en el catálogo. */
public record ProductoRequest(
        @NotBlank String rfidTag,
        @NotBlank String nombre,
        Producto.NutriScore nutriScore,
        @NotNull @Positive Integer plazoCaducidadDias
) {
}
