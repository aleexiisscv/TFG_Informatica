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

    /**
     * Revisa el inventario y genera alertas para los productos que están
     * a punto de caducar o ya han caducado (Fase 13).
     *
     * <p>Se ejecuta sola una vez al día, pero se expone en la interfaz
     * para poder invocarla desde un test —o desde un endpoint de
     * administración— sin esperar al cron. Una tarea programada que solo
     * se puede probar esperando 24 horas es una tarea que nadie prueba.</p>
     *
     * @return número de alertas creadas en esta pasada
     */
    int revisarCaducidades();
}
