package com.example.smartfridge.api.dto;

/**
 * Espejo del ProductoResponse del backend. Los nombres de campo deben
 * coincidir con las claves JSON (Gson mapea por reflexión, sin
 * necesidad de getters/setters ni anotaciones para este caso simple).
 */
public class ProductoDto {

    public String rfidTag;
    public String nombre;
    public String nutriScore;      // "A".."E", o null
    public Integer plazoCaducidadDias;

    public ProductoDto() {
        // Gson necesita poder instanciar el objeto para rellenarlo
    }

    public ProductoDto(String rfidTag, String nombre, String nutriScore, Integer plazoCaducidadDias) {
        this.rfidTag = rfidTag;
        this.nombre = nombre;
        this.nutriScore = nutriScore;
        this.plazoCaducidadDias = plazoCaducidadDias;
    }
}
