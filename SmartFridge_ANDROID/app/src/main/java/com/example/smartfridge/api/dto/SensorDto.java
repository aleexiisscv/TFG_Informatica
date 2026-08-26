package com.example.smartfridge.api.dto;

/**
 * Espejo del SensorResponse del backend.
 *
 * OJO: "tipo" llega en MAYÚSCULAS ("AGUA", "TEMPERATURA", "HUMEDAD",
 * "PUERTA") porque así serializa Jackson el enum TipoSensor del
 * backend. El sistema legacy usaba minúsculas ("agua", "temperatura"...).
 */
public class SensorDto {
    public String tipo;
    public Float medicion;
    public String ultLectura;
}
