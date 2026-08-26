package com.smartfridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de conexión MQTT, mapeadas desde el prefijo
 * "smartfridge.mqtt" de application.yml.
 *
 * Se usa un record en vez de una clase con getters/setters: es inmutable
 * (una vez arrancada la aplicación, la configuración no cambia bajo los
 * pies de nadie) y Spring Boot 3.x lo soporta de forma nativa mediante
 * constructor binding. Sustituye a las constantes `private static final`
 * hardcodeadas del `MQTTBroker` legacy (IP, usuario y contraseña
 * quedaban fijas en el .java y solo se podían cambiar recompilando).
 */
@ConfigurationProperties(prefix = "smartfridge.mqtt")
public record MqttProperties(
        String brokerUrl,
        String clientId,
        String username,
        String password,
        String baseTopic
) {
}
