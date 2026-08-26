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
 * Histórico/auditoría de eventos: entradas y salidas de producto
 * (tipoRegistro = ENTRADA/SALIDA, con "producto" relleno) y alertas de
 * sensor (tipoRegistro = ALERTA, con "sensor" y "medicion" rellenos).
 *
 * Se mantienen "producto" y "sensor" como opcionales (nullable), fieles
 * a la semántica original de "id_producto"/"id_sensor" en la tabla
 * legacy, donde exactamente uno de los dos está presente según el tipo
 * de evento. Esta regla de exclusión mutua se validará a nivel de
 * Service (Fase 3), no aquí, para mantener la entidad JPA como un
 * mapeo fiel del esquema y no mezclar responsabilidades de negocio.
 */
@Entity
@Table(name = "registro")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Registro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_registro", nullable = false, length = 20)
    @NotNull
    private TipoRegistro tipoRegistro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_producto", referencedColumnName = "rfid_tag")
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sensor")
    private Sensor sensor;

    private Float medicion;

    @Column(nullable = false)
    @NotNull
    private LocalDateTime fecha;
}
