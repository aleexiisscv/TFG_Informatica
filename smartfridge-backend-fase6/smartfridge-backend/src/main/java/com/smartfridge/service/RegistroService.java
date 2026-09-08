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

    /**
     * Alerta de caducidad próxima (Fase 13).
     *
     * <p>Se guarda como {@code TipoRegistro.ALERTA} —igual que las
     * alertas de sensor— pero con {@code producto} relleno y
     * {@code sensor} nulo. Rompe deliberadamente la exclusión mutua que
     * documenta la entidad {@code Registro}, y conviene ser explícito
     * sobre por qué: la alternativa era crear un
     * {@code TipoRegistro.CADUCIDAD}, que habría dejado estas alertas
     * FUERA del panel de notificaciones de la app —que consulta
     * {@code /api/registros?tipo=ALERTA}— hasta actualizar también el
     * cliente. Reutilizar ALERTA hace que aparezcan desde el primer día
     * en el sitio donde el usuario ya mira. {@code RegistroResponse} ya
     * expone ambos campos como opcionales, así que la API lo soporta sin
     * cambios.</p>
     *
     * @param producto        producto que va a caducar
     * @param diasRestantes   días que faltan; negativo si ya caducó. Viaja
     *                        en el campo {@code medicion}, que es el que
     *                        la app ya sabe leer de una alerta
     */
    Registro registrarAlertaCaducidad(Producto producto, long diasRestantes);
}
