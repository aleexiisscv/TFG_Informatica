package com.example.smartfridge.api.dto;

/** Espejo del RegistroResponse del backend. sensorTipo llega en MAYÚSCULAS (ver SensorDto). */
public class RegistroDto {
    public Long id;
    public String tipoRegistro;    // "ENTRADA" | "SALIDA" | "ALERTA"
    public String nombreProducto;  // solo relleno si tipoRegistro es ENTRADA/SALIDA
    public String sensorTipo;      // solo relleno si tipoRegistro es ALERTA
    public Float medicion;
    public String fecha;
}
