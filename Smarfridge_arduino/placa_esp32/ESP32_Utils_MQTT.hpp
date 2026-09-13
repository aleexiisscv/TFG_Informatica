// ESP32_Utils_MQTT.hpp
//
// Ciclo de vida de la conexión MQTT (inicialización, reconexión y
// bombeo del loop del cliente). La interpretación de topics/payloads
// vive en MQTT.hpp; aquí solo se gestiona la conexión con el broker.
#ifndef ESP32_UTILS_MQTT_HPP
#define ESP32_UTILS_MQTT_HPP

#include <PubSubClient.h>
#include "Config.h"
#include "MQTT.hpp"

// Configura el cliente MQTT (servidor + callback de recepción) y
// realiza la primera suscripción. Se invoca una única vez desde setup().
void InitMqtt() {
    mqttClient.setServer(MQTT_BROKER_ADDRESS, MQTT_PORT);
    SuscribeMqtt();
    mqttClient.setCallback(OnMqttReceived);
}

// Intenta conectar (o reconectar) con el broker MQTT de forma
// bloqueante, reintentando cada 5 segundos hasta lograrlo.
void ConnectMqtt() {
    while (!mqttClient.connected()) {
        Serial.print("Starting MQTT connection...");
        if (mqttClient.connect(MQTT_CLIENT_NAME, "ubicua", "ubicua")) {
            SuscribeMqtt();
        } else {
            Serial.print("Failed MQTT connection, rc=");
            Serial.print(mqttClient.state());
            Serial.println(" try again in 5 seconds");
            delay(5000);
        }
    }
}

// Debe llamarse en cada iteración del loop(): reconecta si hace falta
// y procesa los mensajes/keepalive del cliente MQTT (PubSubClient).
void HandleMqtt() {
    if (!mqttClient.connected()) {
        ConnectMqtt();
    }
    mqttClient.loop();
}

#endif // ESP32_UTILS_MQTT_HPP
