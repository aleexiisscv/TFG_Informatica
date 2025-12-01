// ESP32_Utils.hpp
#ifndef ESP32_UTILS_HPP
#define ESP32_UTILS_HPP

#include <WiFi.h>
#include "Config.h"
//#include <IPAddress.h> 

void ConnectWiFi_STA(/*bool useStaticIP = false*/) {
    Serial.println("");
    WiFi.mode(WIFI_STA);
    Serial.print("IP address:\t");

    WiFi.begin(ssid, password);
    Serial.print(ssid);
    Serial.println(WiFi.localIP());
    while (WiFi.status() != WL_CONNECTED) { 
        Serial.println("wifi stataus");
        Serial.println(WiFi.status());
        Serial.println(WIFI_STA);
        delay(3000);  
        Serial.print('.'); 
    }

    Serial.println("");
    Serial.print("Iniciado STA:\t");
    Serial.println("ubicua");
    Serial.print("IP address:\t");
    Serial.println(WiFi.status());
    Serial.println(WiFi.localIP());
}

#endif // ESP32_UTILS_HPP
