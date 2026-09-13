package com.smartfridge.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartfridge.model.LecturaSensor;
import com.smartfridge.model.TipoSensor;

/**
 * Acceso a la tabla "lectura_sensor" (histórico aditivo, materia prima
 * de los futuros modelos de IA de la Fase de analítica).
 */
public interface LecturaSensorRepository extends JpaRepository<LecturaSensor, Long> {

    /**
     * Consulta pensada para alimentar gráficas y, más adelante, el
     * conjunto de entrenamiento de un modelo predictivo: "todas las
     * lecturas de temperatura entre el día X y el día Y". Se apoya en
     * el índice compuesto (tipo_sensor, fecha) definido en la entidad.
     */
    List<LecturaSensor> findByTipoSensorAndFechaBetweenOrderByFechaAsc(
            TipoSensor tipoSensor,
            LocalDateTime desde,
            LocalDateTime hasta
    );
}
