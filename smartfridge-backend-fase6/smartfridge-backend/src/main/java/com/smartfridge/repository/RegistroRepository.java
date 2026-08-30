package com.smartfridge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.smartfridge.model.Registro;
import com.smartfridge.model.TipoRegistro;

/**
 * Acceso a la tabla "registro" (auditoría de entradas, salidas y
 * alertas de sensor).
 *
 * <p>CORRECCIÓN — {@code LazyInitializationException} en
 * GET /api/registros: {@code Could not initialize proxy [Sensor#3] - no
 * session}. Mismo diagnóstico y misma solución que en
 * {@link InventarioRepository#findAll()}; aquí el grafo carga las DOS
 * asociaciones porque {@code RegistroController} consulta ambas.</p>
 *
 * <p>{@code Registro.producto} y {@code Registro.sensor} son opcionales
 * por diseño (exactamente una de las dos está rellena según el tipo de
 * evento), así que Hibernate genera LEFT JOIN y las filas sin producto
 * —las alertas— siguen apareciendo. Sustituirlo por un
 * {@code join fetch} escrito a mano habría hecho justo lo contrario:
 * un INNER JOIN silencioso que haría desaparecer del listado
 * precisamente las alertas.</p>
 */
public interface RegistroRepository extends JpaRepository<Registro, Long> {

    @Override
    @EntityGraph(attributePaths = {"producto", "sensor"})
    List<Registro> findAll();

    /** Útil para paneles de actividad reciente o estadísticas por tipo de evento. */
    @EntityGraph(attributePaths = {"producto", "sensor"})
    List<Registro> findByTipoRegistroOrderByFechaDesc(TipoRegistro tipoRegistro);
}
