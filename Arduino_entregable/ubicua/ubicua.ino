// ubicua.ino
//
// Firmware de la capa de Percepción del Frigorífico Inteligente (TFG).
//
// Responsabilidad de este .ino: leer los sensores físicos (DHT11,
// sensor magnético de puerta, sensor de agua) y publicar su estado en
// el broker MQTT bajo el prefijo "frigorifico/". La interpretación de
// esos datos (alertas, inventario, modos de operación) vive por
// completo en el backend (Spring Boot): el ESP32 no mantiene ningún
// estado de negocio en memoria, solo el estado mínimo imprescindible
// para des-rebotar lecturas de sensores (p. ej. estadoAnteriorPuerta).
//
// Fase 8: se elimina por completo el subsistema RFID (lector MFRC522)
// y el modo de operación manual INSERTAR/ELIMINAR asociado a él, ya
// obsoletos. Se deja preparado el punto de extensión
// procesarVisionArtificial(), donde en una fase posterior se integrará
// una ESP32-CAM para reconocimiento visual de productos.

#include <WiFi.h>
#include <PubSubClient.h>
#include <DHT.h>
#include "ESP32_Utils.hpp"
#include "ESP32_Utils_MQTT.hpp"
#include "MQTT.hpp"
#include "Config.h"

// ---------------------------------------------------------------------
// Sensor de temperatura y humedad (DHT11)
// ---------------------------------------------------------------------
#define DHTPIN 13      // Pin del sensor de temperatura y humedad
#define DHTTYPE DHT11  // Tipo de sensor
DHT dht(DHTPIN, DHTTYPE);

// ---------------------------------------------------------------------
// Resto de sensores y actuadores
// ---------------------------------------------------------------------
#define PIN_WATER_SENSOR 14  // Pin del sensor de agua
#define PIN_MAGNETIC     32  // Pin del sensor magnético (puerta)
#define PIN_LED_CALOR    26  // LED indicador de temperatura alta
#define PIN_LED_FRIO     33  // LED indicador de temperatura en rango normal

const int umbralTemperatura = 5;  // Umbral de temperatura en grados Celsius

// Último estado leído del sensor de puerta, usado para detectar
// flancos (transiciones) y evitar publicar en cada iteración del loop.
int estadoAnteriorPuerta = HIGH;

void setup() {
    Serial.begin(115200);

    // Conexión WiFi
    ConnectWiFi_STA();

    // Configuración y primera conexión MQTT
    InitMqtt();

    // Sensor de temperatura y humedad
    dht.begin();

    // Pines de sensores
    pinMode(PIN_WATER_SENSOR, INPUT);
    pinMode(PIN_MAGNETIC, INPUT);

    // Pines de LEDs indicadores
    pinMode(PIN_LED_CALOR, OUTPUT);
    digitalWrite(PIN_LED_CALOR, LOW);
    pinMode(PIN_LED_FRIO, OUTPUT);
    digitalWrite(PIN_LED_FRIO, LOW);
}

void loop() {
    HandleMqtt();
    verificarSensores();

    // Punto de extensión (Fase 9): reconocimiento visual con ESP32-CAM.
    // De momento no hace nada; marca el lugar exacto donde se integrará
    // la captura/inferencia de imagen que sustituirá a la identificación
    // de productos por RFID.
    procesarVisionArtificial();

    delay(7000);  // Publicar cada 7 segundos
}

// ---------------------------------------------------------------------
// Lectura y publicación de sensores
// ---------------------------------------------------------------------
void verificarSensores() {
    // Temperatura y humedad
    float temp = dht.readTemperature();
    float hum = dht.readHumidity();

    // Anomalía de temperatura alta
    if (temp > 35) {
        String mensaje = "Temperatura alta " + String(temp) + " °C";
        PublishMqtt(mensaje, MQTT_TOPIC_ANOMALIAS);
        Serial.println(mensaje);
        digitalWrite(PIN_LED_CALOR, HIGH);
        digitalWrite(PIN_LED_FRIO, LOW);
    } else {
        digitalWrite(PIN_LED_CALOR, LOW);
        digitalWrite(PIN_LED_FRIO, HIGH);
    }

    // Anomalía de humedad alta
    if (hum > 80) {
        String mensaje = "Humedad alta" + String(hum) + " %";
        PublishMqtt(mensaje, MQTT_TOPIC_ANOMALIAS);
        Serial.println(mensaje);
    }

    // Sensor de agua
    int waterState = digitalRead(PIN_WATER_SENSOR);
    if (waterState == HIGH) {
        String mensaje = "Agua detectada";
        PublishMqtt(mensaje, MQTT_TOPIC_ANOMALIAS);
        Serial.println(mensaje);
    }

    // Sensor de puerta: solo se registra como anomalía si permanece
    // abierta al menos 5 segundos, para filtrar aperturas breves.
    int doorState = digitalRead(PIN_MAGNETIC);
    if (doorState != estadoAnteriorPuerta) {
        if (doorState == LOW) {
            delay(5000);
            if (digitalRead(PIN_MAGNETIC) == LOW) {
                String mensaje = "Puerta abierta";
                PublishMqtt(mensaje, MQTT_TOPIC_ANOMALIAS);
                Serial.println(mensaje);
            }
        }
        estadoAnteriorPuerta = doorState;
    }

    // Publicación periódica del estado de todos los sensores.
    // IMPORTANTE: estas cadenas deben coincidir EXACTAMENTE con lo que
    // espera FrigorificoTopicRouter en el backend (Spring Boot).
    PublishMqtt(temp, MQTT_TOPIC_TEMP);
    PublishMqtt(hum, MQTT_TOPIC_HUMI);
    PublishMqtt(waterState ? "Agua detectada" : "Sin agua", MQTT_TOPIC_WATER);
    PublishMqtt(doorState ? "Puerta cerrada" : "Puerta abierta", MQTT_TOPIC_DOOR);
}

// ---------------------------------------------------------------------
// Placeholder de visión artificial (Fase 9)
// ---------------------------------------------------------------------
// Aún sin implementar. Aquí se integrará, en una fase posterior, la
// captura de imagen (ESP32-CAM) y la inferencia (a bordo o delegada a
// un servicio de IA en el backend) para identificar productos que
// entran/salen del frigorífico, sustituyendo a la antigua
// identificación por RFID.
void procesarVisionArtificial() {
    // TODO (Fase 9): captura de fotograma + inferencia de visión por
    // computador para reconocimiento de productos.
}
