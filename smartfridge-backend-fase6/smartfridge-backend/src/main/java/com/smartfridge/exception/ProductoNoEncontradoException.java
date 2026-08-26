package com.smartfridge.exception;

/**
 * Se lanza cuando llega un evento RFID (por MQTT o por la API) cuyo
 * tag no corresponde a ningún producto dado de alta en el catálogo.
 */
public class ProductoNoEncontradoException extends RuntimeException {

    public ProductoNoEncontradoException(String rfidTag) {
        super("No existe ningún producto registrado con el RFID '" + rfidTag + "'");
    }
}
