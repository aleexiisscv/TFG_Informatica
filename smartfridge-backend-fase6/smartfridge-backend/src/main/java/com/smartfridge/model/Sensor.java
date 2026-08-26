package com.smartfridge.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Estado de "última lectura" de cada sensor físico (una fila por tipo
 * de sensor: AGUA, HUMEDAD, TEMPERATURA, PUERTA). Se actualiza con
 * UPDATE, igual que en el sistema legacy, y sigue siendo útil para
 * responder rápido a "¿cuál es el estado actual del frigorífico?" sin
 * tener que agregar sobre una tabla que crece sin límite.
 *
 * El histórico necesario para modelos predictivos de IA (consumo,
 * detección de anomalías) NO vive aquí: se resuelve con la tabla
 * aditiva {@link LecturaSensor}, que guarda una fila por cada lectura
 * recibida por MQTT en lugar de sobrescribir el valor anterior. En la
 * Fase 3, el listener MQTT deberá escribir en ambas tablas a la vez
 * (actualizar Sensor + insertar LecturaSensor) desde el mismo Service,
 * manteniéndolas consistentes.
 */
@Entity
@Table(name = "sensores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    @NotNull
    private TipoSensor tipo;

    @Column(nullable = false)
    private Float medicion;

    @Column(name = "ult_lectura")
    private LocalDateTime ultLectura;
}
