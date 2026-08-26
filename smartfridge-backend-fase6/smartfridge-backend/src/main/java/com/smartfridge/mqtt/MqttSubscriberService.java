package com.smartfridge.mqtt;

import java.nio.charset.StandardCharsets;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Service;

import com.smartfridge.config.MqttProperties;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Puente entre el broker MQTT y el dominio de la aplicación.
 *
 * Diferencia clave con {@code MQTTSuscriber} del sistema legacy: allí
 * {@code suscribeTopic(...)} se invocaba desde dentro de
 * {@code DatabaseServlet.doPost}, es decir, se creaba una conexión MQTT
 * NUEVA cada vez que alguien (la app Android) hacía una petición HTTP al
 * servlet — sin cerrar nunca las anteriores. Aquí la suscripción ocurre
 * una única vez, al arrancar la aplicación ({@link PostConstruct}), y
 * se cierra de forma ordenada al pararla ({@link PreDestroy}); el ciclo
 * de vida de la conexión MQTT queda totalmente desacoplado de las
 * peticiones HTTP que reciba la API REST.
 *
 * Esta clase solo sabe recibir bytes y delegar: toda interpretación del
 * topic/payload vive en {@link FrigorificoTopicRouter} (ACL).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MqttSubscriberService implements MqttCallback {

    private final MqttClient mqttClient;
    private final MqttConnectOptions mqttConnectOptions;
    private final MqttProperties mqttProperties;
    private final FrigorificoTopicRouter router;

    @PostConstruct
    public void iniciar() {
        try {
            mqttClient.setCallback(this);
            mqttClient.connect(mqttConnectOptions);
            mqttClient.subscribe(mqttProperties.baseTopic());
            log.info("Suscrito a '{}' en el broker {}", mqttProperties.baseTopic(), mqttProperties.brokerUrl());
        } catch (MqttException e) {
            // Se registra pero no se relanza: un broker caído al arrancar
            // no debería impedir que la API REST levante (options.setAutomaticReconnect(true)
            // se encargará de reintentar la conexión inicial más adelante
            // solo si llegó a conectar una vez; si falla aquí, se deja
            // constancia clara en el log para diagnóstico).
            log.error("No se pudo conectar/suscribir al broker MQTT '{}': {}",
                    mqttProperties.brokerUrl(), e.getMessage(), e);
        }
    }

    @PreDestroy
    public void detener() {
        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
                log.info("Desconectado del broker MQTT de forma ordenada.");
            }
        } catch (MqttException e) {
            log.warn("Error al desconectar del broker MQTT: {}", e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Conexión MQTT perdida ({}); Paho reintentará automáticamente.", cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        try {
            router.enrutar(topic, payload);
        } catch (Throwable t) {
            // Ampliado de Exception a Throwable: un Error (p. ej. un
            // NoClassDefFoundError si falta una dependencia en tiempo de
            // ejecución, o un StackOverflowError en un parseo recursivo
            // mal escrito) tampoco debe poder tumbar el hilo de callback
            // de Paho. Es una red de seguridad deliberadamente amplia:
            // preferimos loguear y seguir vivos antes que perder la
            // suscripción MQTT entera por un solo mensaje problemático.
            log.error("Error procesando mensaje MQTT de '{}' (payload='{}')", topic, payload, t);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Este servicio solo consume mensajes (suscriptor); no publica,
        // por lo que no hay entregas propias que confirmar aquí.
    }
}
