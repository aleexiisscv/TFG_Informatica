package com.smartfridge.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Unidad física de un producto dentro del frigorífico.
 *
 * En el esquema legacy, "producto_id" era una simple columna String sin
 * una FK declarada a nivel de BBDD (se enlazaba "a mano" en el código
 * Java). Aquí se modela como una relación JPA real: Hibernate garantiza
 * la integridad referencial y permite navegar inventario.getProducto()
 * sin una consulta manual adicional.
 */
@Entity
@Table(name = "inventario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", referencedColumnName = "rfid_tag", nullable = false)
    @NotNull
    private Producto producto;

    @Column(name = "fecha_entrada", nullable = false)
    @NotNull
    private LocalDateTime fechaEntrada;

    /**
     * Calculada como fechaEntrada + producto.plazoCaducidadDias en el
     * momento de la inserción (misma lógica que Inventario.java del
     * backend legacy), pero persistida para no recalcularla en cada
     * lectura y para poder indexarla en consultas de "productos a punto
     * de caducar".
     */
    @Column(name = "fecha_caducidad")
    private LocalDateTime fechaCaducidad;
}
