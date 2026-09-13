package com.smartfridge.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Usuario de la aplicación (móvil o web).
 *
 * Cambios respecto al esquema legacy "users":
 *  1) "pass" (texto plano) -> "password_hash" (BCrypt, vía
 *     PasswordEncoder de Spring Security). El sistema legacy comparaba
 *     contraseñas en claro contra la BBDD, un riesgo de seguridad grave
 *     documentable como hallazgo en la memoria.
 *  2) Se elimina la columna "inventario" (int) del modelo legacy: no tenía
 *     una FK real hacia la tabla "inventario" y su propósito no estaba
 *     implementado en ningún punto del código fuente auditado. Si se
 *     necesita soporte multi-frigorífico/multi-usuario en el futuro, se
 *     recomienda modelarlo como una relación @OneToMany real
 *     (Usuario 1—N Inventario) en vez de un contador suelto.
 */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    @NotBlank
    private String nombre;

    @Column(nullable = false, unique = true, length = 150)
    @NotBlank
    @Email
    private String correo;

    @Column(name = "password_hash", nullable = false)
    @NotBlank
    private String passwordHash;
}
