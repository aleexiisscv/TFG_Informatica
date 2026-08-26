package com.smartfridge.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartfridge.model.Sensor;
import com.smartfridge.model.TipoSensor;

/**
 * Acceso a la tabla "sensores" (estado actual: última lectura por tipo).
 */
public interface SensorRepository extends JpaRepository<Sensor, Long> {

    /**
     * Cada tipo de sensor tiene, como mucho, una fila (ver constraint
     * "unique = true" en {@link Sensor#getTipo()}). Se usa en
     * SensorService para decidir si hay que crear la fila la primera
     * vez o actualizar la existente (upsert a nivel de aplicación).
     */
    Optional<Sensor> findByTipo(TipoSensor tipo);
}
