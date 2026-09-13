package com.smartfridge.mqtt;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/**
 * Mantiene en memoria si el próximo evento RFID debe interpretarse
 * como una entrada (INSERTAR) o una salida (ELIMINAR) de producto.
 *
 * Sustituye a {@code public static String modo} de {@code DatabaseServlet}
 * en el sistema legacy: aquel campo no tenía ninguna garantía de
 * visibilidad entre hilos (no era {@code volatile} ni estaba protegido
 * por ningún lock). Aquí se usa {@link AtomicReference}, que sí
 * garantiza visibilidad y atomicidad en la lectura/escritura sin
 * necesidad de {@code synchronized}.
 *
 * Nota de alcance para la memoria: es un valor EN MEMORIA (no
 * persistido), correcto para un backend de una única instancia — el
 * caso de este TFG. Si en el futuro el backend se escalase
 * horizontalmente (varias instancias detrás de un balanceador), este
 * estado tendría que moverse a algo compartido (BBDD, Redis) para que
 * todas las réplicas vieran el mismo modo; se documenta como
 * limitación conocida.
 *
 * El valor por defecto es INSERTAR porque así lo fija el propio
 * firmware nada más conectarse (ver {@code SuscribeMqtt()} en
 * {@code MQTT.hpp}, que publica "INSERTAR" justo después de suscribirse).
 */
@Component
public class EstadoModoFrigorifico {

    private final AtomicReference<ModoOperacion> modoActual =
            new AtomicReference<>(ModoOperacion.INSERTAR);

    public void actualizar(ModoOperacion nuevoModo) {
        modoActual.set(nuevoModo);
    }

    public ModoOperacion actual() {
        return modoActual.get();
    }
}
