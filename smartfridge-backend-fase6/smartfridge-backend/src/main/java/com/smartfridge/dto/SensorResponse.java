package com.smartfridge.dto;

import java.time.LocalDateTime;

import com.smartfridge.model.TipoSensor;

public record SensorResponse(
        TipoSensor tipo,
        Float medicion,
        LocalDateTime ultLectura
) {
}
