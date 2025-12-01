// MQTT.hpp
#ifndef MQTT_HPP
#define MQTT_HPP

#include "ESP32_Utils_MQTT.hpp"
#include "Config.h"
#include <PubSubClient.h>

const char* MQTT_TOPIC_TEMP = "frigorifico/temperature";
const char* MQTT_TOPIC_HUMI = "frigorifico/humidity";
const char* MQTT_TOPIC_WATER = "frigorifico/water";
const char* MQTT_TOPIC_DOOR = "frigorifico/door";
const char* MQTT_TOPIC_RFID = "frigorifico/rfid";
const char* MQTT_TOPIC_ANOMALIAS= "frigorifico/anomalias";
const char* MQTT_TOPIC_PRODUCTOS = "frigorifico/productos";
const char* MQTT_TOPIC_MODO = "frigorifico/modo";


WiFiClient espClient;
PubSubClient mqttClient(espClient);


String content;
void OnMqttReceived(char* topic, byte* payload, unsigned int length) {
    Serial.print("Received on ");
    Serial.print(topic);
    Serial.print(": ");

    content = "";    
    for (size_t i = 0; i < length; i++) {
        content.concat((char)payload[i]);
    }
    Serial.print(content);
    Serial.println();
}

void SuscribeMqtt() {
    mqttClient.subscribe("Mosquitto_Broker");
    mqttClient.publish(MQTT_TOPIC_MODO, "INSERTAR");
    mqttClient.setCallback(OnMqttReceived);
}

String payload;
void PublishMqtt(String data, const char* topic) {
    payload = data;
    mqttClient.publish(topic, (char*)payload.c_str());
}

void PublishMqtt(float data, const char* topic) {
    payload = String(data);
    mqttClient.publish(topic, (char*)payload.c_str());
}






#endif // MQTT_HPP
