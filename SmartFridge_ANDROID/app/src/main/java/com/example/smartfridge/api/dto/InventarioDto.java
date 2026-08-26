package com.example.smartfridge.api.dto;

/** Espejo del InventarioResponse del backend (ya incluye nombreProducto y nutriScore aplanados). */
public class InventarioDto {

    public Long id;
    public String rfidTag;
    public String nombreProducto;
    public String nutriScore;
    public String fechaEntrada;    // ISO-8601, p. ej. "2026-08-26T10:15:30"
    public String fechaCaducidad;
}
