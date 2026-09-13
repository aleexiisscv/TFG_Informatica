package com.smartfridge.config;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fabrica el cliente Paho y sus opciones de conexión como Beans de Spring.
 *
 * Diferencia deliberada con el patrón legacy ({@code MQTTBroker}, un
 * singleton manual con {@code getInstance()}): aquí el ciclo de vida del
 * objeto lo gestiona el contenedor de Spring (un único Bean, inyectable
 * por constructor, mockeable en tests), no una clase que se autoinstancia
 * la primera vez que alguien la usa. Esta clase solo CONFIGURA el cliente;
 * la conexión real al broker y la suscripción a topics se hacen en
 * {@link com.smartfridge.mqtt.MqttSubscriberService}, separando
 * "cómo se construye" de "cuándo se conecta y qué hace al recibir datos"
 * (principio de responsabilidad única).
 */
@Configuration
@EnableConfigurationProperties(MqttProperties.class)
public class MqttConfig {

    @Bean
    public MqttClient mqttClient(MqttProperties properties) throws MqttException {
        return new MqttClient(
                properties.brokerUrl(),
                properties.clientId(),
                new MemoryPersistence()
        );
    }

    @Bean
    public MqttConnectOptions mqttConnectOptions(MqttProperties properties) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(properties.username());
        options.setPassword(properties.password().toCharArray());
        options.setCleanSession(true);

        // Mejora sobre el sistema legacy: si se cae la conexión con el
        // broker (Wi-Fi inestable del ESP32, reinicio del Mosquitto, etc.)
        // Paho reintenta solo. El código legacy no tenía ninguna estrategia
        // de reconexión: si se perdía la conexión, había que reiniciar
        // manualmente el backend.
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);

        return options;
    }
}
