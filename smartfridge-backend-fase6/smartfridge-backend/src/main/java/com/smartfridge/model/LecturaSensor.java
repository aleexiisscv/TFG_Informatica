package com.smartfridge.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Serie temporal de lecturas de sensor: una fila por cada medición
 * recibida vía MQTT, sin sobrescribir la anterior.
 *
 * Resuelve la limitación señalada en {@link Sensor}: la tabla "sensores"
 * original solo guardaba el último valor (UPDATE in place), lo que hacía
 * imposible entrenar un modelo predictivo (consumo, anomalías) por falta
 * de histórico. Esta tabla es aditiva (solo INSERT) y crece de forma
 * indefinida — es intencional, es la materia prima de la Fase de IA.
 *
 * Se denormaliza "tipoSensor" (copiado desde Sensor.tipo en el momento
 * de la inserción) para poder consultar series por tipo sin necesidad
 * de JOIN, ya que las consultas de analítica ("temperatura de los
 * últimos 7 días") son el caso de uso principal de esta tabla.
 *
 * Nota de volumen: con una lectura cada pocos segundos desde el ESP32,
 * esta tabla puede crecer rápido. Para el alcance de un TFG no es un
 * problema, pero se documenta como punto a vigilar (candidato a una
 * tarea de purgado/agregación periódica, o a un particionado por fecha
 * si en el futuro se despliega en producción real).
 */
@Entity
@Table(
    name = "lectura_sensor",
    indexes = {
        @Index(name = "idx_lectura_sensor_tipo_fecha", columnList = "tipo_sensor, fecha"),
        @Index(name = "idx_lectura_sensor_sensor_id", columnList = "sensor_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LecturaSensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sensor_id", nullable = false)
    @NotNull
    private Sensor sensor;

    /** Denormalizado desde sensor.tipo para consultas de analítica sin JOIN. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_sensor", nullable = false, length = 20)
    @NotNull
    private TipoSensor tipoSensor;

    @Column(nullable = false)
    @NotNull
    private Float valor;

    @Column(nullable = false)
    @NotNull
    private LocalDateTime fecha;
}
