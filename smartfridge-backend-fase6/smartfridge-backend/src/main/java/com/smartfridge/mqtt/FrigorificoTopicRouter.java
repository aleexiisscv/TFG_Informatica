package com.smartfridge.mqtt;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.smartfridge.exception.InventarioVacioException;
import com.smartfridge.exception.ProductoNoEncontradoException;
import com.smartfridge.model.TipoSensor;
import com.smartfridge.service.InventarioService;
import com.smartfridge.service.RegistroService;
import com.smartfridge.service.SensorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Anti-Corruption Layer (ACL) entre el formato "de cable" que publica
 * el ESP32 y el modelo de dominio de este backend.
 *
 * El firmware Arduino mezcla, en el mismo flujo, números en crudo
 * ("23.50" para temperatura) y frases en español pensadas para humanos
 * ("Agua detectada", "Temperatura alta 36.20 °C"). Ese formato es
 * responsabilidad del firmware, no del dominio: si mañana cambia el
 * formato de alguno de estos mensajes, el único archivo que debería
 * cambiar es este, no los Services ni las entidades JPA.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FrigorificoTopicRouter {

    private static final String PREFIJO_TOPIC = "frigorifico/";

    /** Extrae el primer número (entero o decimal) de una cadena, p. ej. de "Humedad alta80.00 %". */
    private static final Pattern PATRON_NUMERO = Pattern.compile("[0-9]+(\\.[0-9]+)?");

    private final SensorService sensorService;
    private final InventarioService inventarioService;
    private final RegistroService registroService;
    private final EstadoModoFrigorifico estadoModo;

    public void enrutar(String topicCompleto, String payload) {
        String subTopic = topicCompleto.startsWith(PREFIJO_TOPIC)
                ? topicCompleto.substring(PREFIJO_TOPIC.length())
                : topicCompleto;

        switch (subTopic) {
            case "temperature" ->
                    sensorService.registrarLectura(TipoSensor.TEMPERATURA, parseFloatSeguro(payload));

            case "humidity" ->
                    sensorService.registrarLectura(TipoSensor.HUMEDAD, parseFloatSeguro(payload));

            case "water" ->
                    sensorService.registrarLectura(TipoSensor.AGUA, parseEstadoBinario(payload, "Agua detectada"));

            case "door" ->
                    sensorService.registrarLectura(TipoSensor.PUERTA, parseEstadoBinario(payload, "Puerta abierta"));

            case "modo" -> actualizarModo(payload);

            case "rfid" -> procesarEventoRfid(payload.trim());

            case "anomalias" -> procesarAnomalia(payload);

            default ->
                    log.warn("Topic MQTT no reconocido: '{}' (payload='{}')", topicCompleto, payload);
        }
    }

    // ------------------------------------------------------------------
    // frigorifico/modo
    // ------------------------------------------------------------------
    private void actualizarModo(String payload) {
        try {
            ModoOperacion nuevoModo = ModoOperacion.valueOf(payload.trim().toUpperCase());
            estadoModo.actualizar(nuevoModo);
            log.info("Modo de operación actualizado a {}", nuevoModo);
        } catch (IllegalArgumentException e) {
            log.warn("Valor de modo no reconocido: '{}'. Se mantiene el modo actual ({}).",
                    payload, estadoModo.actual());
        }
    }

    // ------------------------------------------------------------------
    // frigorifico/rfid
    // ------------------------------------------------------------------
    private void procesarEventoRfid(String uid) {
        ModoOperacion modo = estadoModo.actual();
        try {
            if (modo == ModoOperacion.INSERTAR) {
                inventarioService.anadirProducto(uid);
            } else {
                inventarioService.retirarProducto(uid);
            }
        } catch (ProductoNoEncontradoException | InventarioVacioException e) {
            // Errores de negocio esperables (tarjeta no registrada como
            // producto, o intento de retirar algo que ya no está en el
            // inventario): se dejan como aviso y NO se propagan. Un
            // evento MQTT "inválido" desde el punto de vista de negocio
            // no debe tumbar nada ni afectar a los siguientes mensajes.
            log.warn("No se pudo procesar el evento RFID '{}' en modo {}: {}", uid, modo, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // frigorifico/anomalias
    // ------------------------------------------------------------------
    private void procesarAnomalia(String payload) {
        if (payload.contains("Temperatura alta")) {
            registroService.registrarAlerta(TipoSensor.TEMPERATURA, extraerNumero(payload));
        } else if (payload.contains("Humedad alta")) {
            registroService.registrarAlerta(TipoSensor.HUMEDAD, extraerNumero(payload));
        } else if (payload.contains("Puerta abierta")) {
            registroService.registrarAlerta(TipoSensor.PUERTA, 1f);
        } else if (payload.contains("Agua detectada")) {
            registroService.registrarAlerta(TipoSensor.AGUA, 1f);
        } else {
            log.warn("Anomalía no reconocida: '{}'", payload);
        }
    }

    private Float extraerNumero(String payload) {
        Matcher m = PATRON_NUMERO.matcher(payload);
        if (m.find()) {
            return Float.parseFloat(m.group());
        }
        log.warn("No se pudo extraer un valor numérico de la anomalía: '{}'", payload);
        return null;
    }

    // ------------------------------------------------------------------
    // Sensores numéricos/binarios (temperature, humidity, water, door)
    // ------------------------------------------------------------------
    private float parseFloatSeguro(String payload) {
        try {
            return Float.parseFloat(payload.trim());
        } catch (NumberFormatException e) {
            log.warn("Payload numérico inválido en topic de sensor: '{}'", payload);
            return 0f;
        }
    }

    private float parseEstadoBinario(String payload, String valorPositivo) {
        return valorPositivo.equalsIgnoreCase(payload.trim()) ? 1f : 0f;
    }
}
