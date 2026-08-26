package com.smartfridge.service;

import com.smartfridge.model.Sensor;
import com.smartfridge.model.TipoSensor;

/**
 * Lógica de negocio asociada a las lecturas de sensor. Se define como
 * interfaz (y no directamente la implementación) para poder mockearla
 * en los tests de {@code FrigorificoTopicRouter} sin levantar una base
 * de datos real, y para dejar la puerta abierta a una segunda
 * implementación (por ejemplo, una que además publique un evento de
 * dominio cuando se supere un umbral) sin tocar a quien la consume.
 */
public interface SensorService {

    /**
     * Registra una lectura de sensor: actualiza el estado "actual" y
     * añade una entrada al histórico. Ver {@link SensorServiceImpl}
     * para el detalle de por qué ambas escrituras van en la misma
     * transacción.
     *
     * @param tipo  tipo de sensor (ya traducido desde el topic MQTT
     *              por el {@code FrigorificoTopicRouter})
     * @param valor valor numérico ya normalizado (p. ej. 1.0/0.0 para
     *              sensores binarios como agua o puerta)
     * @return el estado actualizado del sensor
     */
    Sensor registrarLectura(TipoSensor tipo, float valor);
}
