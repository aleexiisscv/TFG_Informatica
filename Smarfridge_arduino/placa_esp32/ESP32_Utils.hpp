// ESP32_Utils.hpp
//
// Utilidades de conectividad WiFi para el nodo ESP32 de la capa de
// Percepción. No depende de ningún sensor concreto: solo establece la
// conexión de red sobre la que viajará después el tráfico MQTT.
#ifndef ESP32_UTILS_HPP
#define ESP32_UTILS_HPP

#include <WiFi.h>
#include "Config.h"

// Conecta el ESP32 a la red WiFi configurada en Config.h en modo
// estación (STA) y bloquea hasta que la conexión se establece.
void ConnectWiFi_STA() {
    Serial.println("");
    WiFi.mode(WIFI_STA);
    Serial.print("Conectando a SSID:\t");
    Serial.println(ssid);

    WiFi.begin(ssid, password);
    while (WiFi.status() != WL_CONNECTED) {
        delay(3000);
        Serial.print('.');
    }

    Serial.println("");
    Serial.print("Conectado. IP address:\t");
    Serial.println(WiFi.localIP());
}

#endif // ESP32_UTILS_HPP
