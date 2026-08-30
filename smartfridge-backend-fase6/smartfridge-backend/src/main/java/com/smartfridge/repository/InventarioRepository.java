package com.smartfridge.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.smartfridge.model.Inventario;

/**
 * Acceso a la tabla "inventario" (unidades físicas de producto
 * actualmente dentro del frigorífico).
 */
public interface InventarioRepository extends JpaRepository<Inventario, Long> {

    /**
     * CORRECCIÓN — {@code LazyInitializationException} en GET /api/inventario.
     *
     * <p>Síntoma: al abrir el Dashboard, el servidor respondía
     * {@code Could not initialize proxy [Producto#brick_leche_entera] -
     * no session}.</p>
     *
     * <p>Causa: {@code Inventario.producto} es {@code FetchType.LAZY} y
     * el proyecto tiene {@code spring.jpa.open-in-view: false} (correcto:
     * el "Open Session In View" es un anti-patrón en una API REST). Los
     * métodos de {@code SimpleJpaRepository} abren su propia transacción
     * y la CIERRAN al devolver, así que las entidades llegan al
     * controlador ya desligadas, con {@code producto} como un proxy sin
     * sesión. Llamar a {@code getRfidTag()} funcionaba —el proxy conoce
     * su propia clave— pero {@code getNombre()} explotaba.</p>
     *
     * <p>Se resuelve con {@code @EntityGraph} y no con
     * {@code @Transactional} en el controlador por dos razones:</p>
     * <ol>
     *   <li><b>Capas.</b> Abrir una transacción desde la capa web sería
     *       meter una preocupación de persistencia en el transporte,
     *       justo lo contrario del criterio que sigue el resto del
     *       proyecto.</li>
     *   <li><b>Rendimiento.</b> {@code @Transactional} habría hecho que
     *       funcionase, pero cada fila seguiría disparando un SELECT
     *       extra al tocar su producto: el problema N+1. El grafo trae
     *       inventario y producto en UNA sola consulta con JOIN.</li>
     * </ol>
     */
    @Override
    @EntityGraph(attributePaths = "producto")
    List<Inventario> findAll();

    /** Todas las unidades de un producto, de la más antigua a la más nueva. */
    @EntityGraph(attributePaths = "producto")
    List<Inventario> findByProducto_RfidTagOrderByFechaEntradaAsc(String rfidTag);

    /**
     * La unidad más antigua de un producto: es la que retira
     * InventarioService cuando se detecta una salida. Lógica FIFO
     * expresada como consulta derivada en vez de un
     * "DELETE ... ORDER BY ... LIMIT 1" manual.
     */
    @EntityGraph(attributePaths = "producto")
    Optional<Inventario> findFirstByProducto_RfidTagOrderByFechaEntradaAsc(String rfidTag);
}
