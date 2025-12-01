// ESP32_Utils_MQTT.hpp
#ifndef ESP32_UTILS_MQTT_HPP
#define ESP32_UTILS_MQTT_HPP

#include <PubSubClient.h>
#include "Config.h"
#include "MQTT.hpp"



void InitMqtt() {
    mqttClient.setServer(MQTT_BROKER_ADDRESS, MQTT_PORT);
    SuscribeMqtt();
    mqttClient.setCallback(OnMqttReceived);
}

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

void HandleMqtt() {
    if (!mqttClient.connected()) {
        ConnectMqtt();
    }
    mqttClient.loop();
}

#endif // ESP32_UTILS_MQTT_HPP
