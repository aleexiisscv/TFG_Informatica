package com.smartfridge.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartfridge.model.Inventario;

/**
 * Acceso a la tabla "inventario" (unidades físicas de producto
 * actualmente dentro del frigorífico).
 */
public interface InventarioRepository extends JpaRepository<Inventario, Long> {

    /** Todas las unidades de un producto, de la más antigua a la más nueva. */
    List<Inventario> findByProducto_RfidTagOrderByFechaEntradaAsc(String rfidTag);

    /**
     * La unidad más antigua de un producto: es la que retira
     * InventarioService cuando el ESP32 detecta una salida por RFID
     * (misma lógica FIFO que "eliminarProducto" en el sistema legacy,
     * pero ahora expresada como una consulta derivada en vez de un
     * "DELETE ... ORDER BY ... LIMIT 1" manual).
     */
    Optional<Inventario> findFirstByProducto_RfidTagOrderByFechaEntradaAsc(String rfidTag);
}
