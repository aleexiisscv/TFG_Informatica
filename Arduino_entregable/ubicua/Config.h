// Config.h
#ifndef CONFIG_H
#define CONFIG_H

//#include <IPAddress.h>

// Configuración del WiFi
const char* ssid = "AlexisS23";        // Nombre de la red WiFii
const char* password = "ubicuagg";  // Contraseña de la red
const char* hostname = "ESP32Frigorifico";  // Nombre del dispositivo

// Configuración del servidor MQTT (IP del broker Mosquitto)
const char* MQTT_BROKER_ADDRESS = "192.168.116.180";  // Cambia la IP por la de tu broker
const uint16_t MQTT_PORT = 1883;  // Puerto del broker MQTT
const char* MQTT_CLIENT_NAME = "ESP32Cliente";  // Nombre del cliente MQTT
//pruebaaa sync 22222

#endif // CONFIG_H

