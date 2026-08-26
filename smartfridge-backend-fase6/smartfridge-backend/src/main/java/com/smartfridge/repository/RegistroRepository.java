package com.smartfridge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartfridge.model.Registro;
import com.smartfridge.model.TipoRegistro;

/**
 * Acceso a la tabla "registro" (auditoría de entradas, salidas y
 * alertas de sensor).
 */
public interface RegistroRepository extends JpaRepository<Registro, Long> {

    /** Útil para paneles de actividad reciente o estadísticas por tipo de evento. */
    List<Registro> findByTipoRegistroOrderByFechaDesc(TipoRegistro tipoRegistro);
}
