
#include <dummy.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <DHT.h>
#include <SPI.h>
#include <MFRC522v2.h>
#include "ESP32_Utils.hpp"
#include "ESP32_Utils_MQTT.hpp"
#include "MQTT.hpp"
#include "Config.h"
#include <PN5180ISO14443.h>
#include <MFRC522DriverSPI.h>
#include <MFRC522DriverPinSimple.h>
#include <MFRC522Debug.h>

// Definición de pines del sensor DHT
#define DHTPIN 13      // Pin del sensor de temperatura y humedad
#define DHTTYPE DHT11  // Tipo de sensor (ajusta según tu sensor)

// Definir pines del módulo RFID (SPI)
MFRC522DriverPinSimple ss_pin(5);
MFRC522DriverSPI driver{ss_pin}; // Create SPI driver.
MFRC522 mfrc522{driver};  // Create MFRC522 instance.

// Definir pines de los otros sensores y LED
#define PIN_WATER_SENSOR 14   // Pin del sensor de agua
#define PIN_MAGNETIC    32    // Pin del sensor magnético (puerta)
#define PIN_LED_CALOR 26            // Pin del LED (encendido si la temperatura es alta)
#define PIN_LED_FRIO 33            // Pin del LED (encendido si la temperatura es alta)

// Umbral de temperatura para el LED
const int umbralTemperatura = 5;  // Umbral de temperatura en grados Celsius

// Instanciar el sensor de temperatura y humedad
DHT dht(DHTPIN, DHTTYPE);



// Estructura para almacenar anomalías
struct Anomalia {
    float temperatura;
    float humedad;
    String mensaje;
    unsigned long timestamp;
};
std::vector<Anomalia> historialAnomalias;

// Estructura del manejo de los productos
struct Producto {
    String nombre;
    String uid;
    int cantidad;
};

std::vector<Producto> productos = {
    {"Leche", "", 0},
    {"Yogures", "", 0},
    {"Quesos", "", 0}
};

// Variable global para almacenar el estado anterior de la puerta
int estadoAnteriorPuerta = HIGH;
void setup() {
    Serial.begin(115200);  // Inicializar puerto serie


    //conexion wifi
    ConnectWiFi_STA();     // Conectar al WiFi

    // Configurar MQTT
    InitMqtt();            // Configurar MQTT

    // Inicializar el sensor de temperatura y humedad
    dht.begin();

    // Inicializar el módulo RFID
    SPI.begin();            // Inicializar el bus SPI
    mfrc522.PCD_Init(); // Inicializar el módulo RFID
    Serial.println(F("*****************************"));
    Serial.println(F("MFRC522 Digital self test"));
    Serial.println(F("*****************************"));
    MFRC522Debug::PCD_DumpVersionToSerial(mfrc522, Serial);  // Show version of PCD - MFRC522 Card Reader.
    Serial.println(F("-----------------------------"));
    Serial.println(F("Only known versions supported"));
    Serial.println(F("-----------------------------"));
    Serial.println(F("Performing test..."));
    bool result = mfrc522.PCD_PerformSelfTest(); // Perform the test.
    Serial.println(F("-----------------------------"));
    Serial.print(F("Result: "));
    if (result)
      Serial.println(F("RFID LISTO"));
    
    else
      Serial.println(F("Fallo en el RFID"));
    Serial.println();

    // Configurar los pines de los sensores como entradas
    pinMode(PIN_WATER_SENSOR, INPUT);
    pinMode(PIN_MAGNETIC, INPUT);

    // Configurar el pin del LED como salida
    pinMode(PIN_LED_CALOR, OUTPUT);
    digitalWrite(PIN_LED_CALOR, LOW);  // Asegurar que el LED del calor esté apagado al iniciar
    pinMode(PIN_LED_FRIO, OUTPUT);
    digitalWrite(PIN_LED_FRIO, LOW);  // Asegurar que el LED del frio esté apagado al iniciar
}

void loop() { 
    
    verificarTarjetasRFID();
    
    HandleMqtt();
    verificarSensores();

    delay(7000);  // Publicar cada 7 segundos
}
void verificarTarjetasRFID(){
    if ( ! mfrc522.PICC_IsNewCardPresent())
        return;

    // Select one of the cards
    if ( ! mfrc522.PICC_ReadCardSerial())
        return;

    // Leer el UID de la tarjeta
    String uid = "";
    for (byte i = 0; i < mfrc522.uid.size; i++) {
        uid += String(mfrc522.uid.uidByte[i] < 0x10 ? "0" : "") + String(mfrc522.uid.uidByte[i], HEX);
    }
    uid.toUpperCase();
    Serial.print("Card UID: ");
    Serial.println(uid);

    PublishMqtt(uid, MQTT_TOPIC_RFID);

}
void verificarSensores() {
    // Leer temperatura y humedad
    float temp = dht.readTemperature();
    float hum = dht.readHumidity();

    // Verificar anomalías de temperatura
    if (temp > 35) {
        String mensaje = "Temperatura alta " + String(temp) + " °C";
        PublishMqtt(mensaje, MQTT_TOPIC_ANOMALIAS);
        Serial.println(mensaje);
        digitalWrite(PIN_LED_CALOR, HIGH);
        digitalWrite(PIN_LED_FRIO, LOW);
    }else{
        digitalWrite(PIN_LED_CALOR, LOW);
        digitalWrite(PIN_LED_FRIO, HIGH);
        
    }


    // Verificar anomalías de humedad
    if (hum > 80) {
        String mensaje = "Humedad alta" + String(hum) + " %";
        PublishMqtt(mensaje, MQTT_TOPIC_ANOMALIAS);
        Serial.println(mensaje);
    }

    // Verificar sensor de agua
    int waterState = digitalRead(PIN_WATER_SENSOR);
    if (waterState == HIGH) {
        String mensaje = "Agua detectada";
        PublishMqtt(mensaje, MQTT_TOPIC_ANOMALIAS);
        Serial.println(mensaje);
    } 

    // Verificar sensor de puerta
    int doorState = digitalRead(PIN_MAGNETIC);
    if (doorState != estadoAnteriorPuerta) {
        if (doorState == LOW) {
            delay(5000); // Esperar 5 segundos
            if (digitalRead(PIN_MAGNETIC) == LOW) {
                String mensaje = "Puerta abierta";
                PublishMqtt(mensaje, MQTT_TOPIC_ANOMALIAS);
                Serial.println(mensaje);
            }
        } 
        
        estadoAnteriorPuerta = doorState; // Actualizar el estado anterior de la puerta
    }


    // Publicar datos de los sensores
    PublishMqtt(temp, MQTT_TOPIC_TEMP);
    PublishMqtt(hum, MQTT_TOPIC_HUMI);
    PublishMqtt(waterState ? "Agua detectada" : "Sin agua", MQTT_TOPIC_WATER);
    PublishMqtt(doorState ? "Puerta cerrada" : "Puerta abierta", MQTT_TOPIC_DOOR);
}




