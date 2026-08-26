package com.smartfridge.service;

import com.smartfridge.model.Producto;
import com.smartfridge.model.Registro;
import com.smartfridge.model.TipoSensor;

/**
 * Auditoría de eventos de negocio: entradas y salidas de producto, y
 * alertas de sensor. Es la única puerta de escritura hacia la tabla
 * "registro" — ni {@code InventarioService} ni el router escriben en
 * ella directamente.
 */
public interface RegistroService {

    Registro registrarEntrada(Producto producto);

    Registro registrarSalida(Producto producto);

    /**
     * @param tipoSensor sensor que originó la alerta
     * @param medicion   valor asociado (temperatura/humedad en grados o
     *                   porcentaje; 1.0f para alertas binarias como
     *                   "puerta abierta" o "agua detectada", igual que
     *                   en {@code SensorService})
     */
    Registro registrarAlerta(TipoSensor tipoSensor, Float medicion);
}
