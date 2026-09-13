package com.smartfridge.model;

/**
 * Tipo de evento almacenado en la tabla "registro" (histórico/auditoría
 * de movimientos de inventario y alertas de sensores).
 */
public enum TipoRegistro {
    ENTRADA,
    SALIDA,
    ALERTA
}
