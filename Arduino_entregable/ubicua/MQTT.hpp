// MQTT.hpp
//
// Definición de topics MQTT y funciones de publicación/recepción.
//
// Fase 8: se retiran los topics "frigorifico/rfid" (lector MFRC522,
// eliminado del hardware), "frigorifico/modo" (modo manual
// INSERTAR/ELIMINAR, ya no existe en el backend) y
// "frigorifico/productos" (el inventario se gestiona íntegramente en
// el backend y su base de datos, nunca en el ESP32).
//
// IMPORTANTE: las cadenas usadas como payload en ubicua.ino deben
// coincidir EXACTAMENTE (mayúsculas/minúsculas y acentos incluidos)
// con lo que interpreta FrigorificoTopicRouter en el backend, ya que
// este actúa como Anti-Corruption Layer basado en comparación de texto.
#ifndef MQTT_HPP
#define MQTT_HPP

#include "ESP32_Utils_MQTT.hpp"
#include "Config.h"
#include <PubSubClient.h>

const char* MQTT_TOPIC_TEMP = "frigorifico/temperature";
const char* MQTT_TOPIC_HUMI = "frigorifico/humidity";
const char* MQTT_TOPIC_WATER = "frigorifico/water";
const char* MQTT_TOPIC_DOOR = "frigorifico/door";
const char* MQTT_TOPIC_ANOMALIAS = "frigorifico/anomalias";

WiFiClient espClient;
PubSubClient mqttClient(espClient);

// Buffer reutilizado para volcar a Serial el contenido de los mensajes
// MQTT entrantes (solo con fines de depuración; el ESP32 no actúa en
// base a comandos recibidos por MQTT).
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

// Suscripción MQTT del cliente. Se invoca al inicializar y en cada
// reconexión (ConnectMqtt).
void SuscribeMqtt() {
    mqttClient.subscribe("Mosquitto_Broker");
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
