package com.smartfridge.exception;

/**
 * Se lanza cuando se intenta retirar (modo ELIMINAR) un producto cuyo
 * RFID no tiene ninguna unidad presente en el inventario actual.
 */
public class InventarioVacioException extends RuntimeException {

    public InventarioVacioException(String rfidTag) {
        super("No hay ninguna unidad en el inventario para el RFID '" + rfidTag + "'");
    }
}
