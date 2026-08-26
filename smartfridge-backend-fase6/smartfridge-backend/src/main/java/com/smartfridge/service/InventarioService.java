package com.smartfridge.service;

import com.smartfridge.model.Inventario;

/**
 * Lógica de negocio de entradas y salidas de producto en el
 * frigorífico. Cada llamada delega la auditoría correspondiente en
 * {@link RegistroService}: InventarioService nunca escribe en
 * "registro" directamente.
 */
public interface InventarioService {

    /**
     * Da de alta una nueva unidad física del producto identificado por
     * {@code rfidTag}, calculando su fecha de caducidad a partir del
     * plazo definido en el catálogo, y audita una ENTRADA.
     *
     * @throws com.smartfridge.exception.ProductoNoEncontradoException
     *         si el RFID no corresponde a ningún producto del catálogo
     */
    Inventario anadirProducto(String rfidTag);

    /**
     * Retira la unidad más antigua (FIFO) del producto identificado por
     * {@code rfidTag} y audita una SALIDA.
     *
     * @throws com.smartfridge.exception.InventarioVacioException
     *         si no queda ninguna unidad de ese producto en el inventario
     */
    Inventario retirarProducto(String rfidTag);
}
