package com.smartfridge.model;

/**
 * Catálogo de sensores físicos de la capa de Percepción (ESP32).
 * Sustituye a la columna libre "tipo" (String) de la tabla legacy
 * "sensores", que aceptaba cualquier texto sin validación.
 */
public enum TipoSensor {
    AGUA,
    HUMEDAD,
    TEMPERATURA,
    PUERTA
}
