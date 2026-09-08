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

    /**
     * ¿Ya se avisó hoy de la caducidad de este producto? (Fase 13)
     *
     * <p>El motor de caducidades es idempotente por día gracias a esta
     * consulta. Sin ella, un reinicio del backend a media mañana volvería
     * a lanzar la tarea y duplicaría todas las alertas; y con un margen
     * de dos días, cada producto generaría además un aviso el día 2 y
     * otro el día 1. Repetir el recordatorio cada día es intencionado —
     * la urgencia aumenta— pero repetirlo tres veces la misma mañana solo
     * es ruido.</p>
     *
     * <p>No lleva {@code @EntityGraph}: devuelve un booleano, no
     * entidades, así que no hay ninguna asociación que inicializar.</p>
     */
    boolean existsByTipoRegistroAndProducto_RfidTagAndFechaGreaterThanEqual(
            TipoRegistro tipoRegistro, String rfidTag, java.time.LocalDateTime desde);
}
