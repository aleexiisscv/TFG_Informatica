// Config.h
//
// Parámetros de configuración del nodo ESP32 (capa de Percepción).
// Fase 8: sin cambios funcionales respecto a fases anteriores; solo se
// documenta con más detalle de cara a la memoria del TFG.
#ifndef CONFIG_H
#define CONFIG_H

// ---------------------------------------------------------------------
// Configuración de red WiFi
// ---------------------------------------------------------------------
const char* ssid = "VodafoneSw";            // Nombre de la red WiFi
const char* password = "Vodafone.Sw";       // Contraseña de la red
const char* hostname = "ESP32Frigorifico";  // Nombre del dispositivo en la red

// ---------------------------------------------------------------------
// Configuración del broker MQTT (Mosquitto)
// ---------------------------------------------------------------------
const char* MQTT_BROKER_ADDRESS = "192.168.0.192";  // IP del broker
const uint16_t MQTT_PORT = 1883;                      // Puerto del broker MQTT
const char* MQTT_CLIENT_NAME = "ESP32Cliente";         // Nombre del cliente MQTT

#endif // CONFIG_H
