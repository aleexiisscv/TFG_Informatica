package com.smartfridge.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Producto catalogado por su etiqueta RFID (MFRC522).
 *
 * Decisión de diseño: se mantiene rfid_tag como clave primaria natural,
 * igual que en el esquema legacy, ya que es el identificador físico que
 * emite el lector RFID y evita un JOIN adicional al registrar
 * entradas/salidas desde el listener MQTT.
 */
@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto {

    @Id
    @Column(name = "rfid_tag", length = 32)
    @NotBlank
    private String rfidTag;

    @Column(nullable = false, length = 150)
    @NotBlank
    private String nombre;

    /**
     * Nutri-Score (A-E). En el esquema legacy se guardaba como String sin
     * restricción; se tipa como enum para garantizar integridad a nivel de
     * aplicación además de a nivel de BBDD.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "nutri_score", length = 1)
    private NutriScore nutriScore;

    /**
     * Plazo de caducidad en días desde la fecha de entrada al inventario.
     * Se usa para calcular Inventario.fechaCaducidad al registrar una entrada.
     */
    @Column(name = "plazo_caducidad", nullable = false)
    @NotNull
    @Positive
    private Integer plazoCaducidadDias;

    public enum NutriScore { A, B, C, D, E }
}
