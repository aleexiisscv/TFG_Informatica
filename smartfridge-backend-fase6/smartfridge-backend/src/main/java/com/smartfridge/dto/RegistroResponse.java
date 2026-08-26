package com.smartfridge.dto;

import java.time.LocalDateTime;

import com.smartfridge.model.TipoRegistro;
import com.smartfridge.model.TipoSensor;

/**
 * Representación pública de un Registro. Solo uno de
 * {@code nombreProducto} / {@code sensorTipo} viene relleno, según si
 * tipoRegistro es ENTRADA/SALIDA o ALERTA (ver la nota de exclusión
 * mutua en la entidad Registro).
 */
public record RegistroResponse(
        Long id,
        TipoRegistro tipoRegistro,
        String nombreProducto,
        TipoSensor sensorTipo,
        Float medicion,
        LocalDateTime fecha
) {
}
