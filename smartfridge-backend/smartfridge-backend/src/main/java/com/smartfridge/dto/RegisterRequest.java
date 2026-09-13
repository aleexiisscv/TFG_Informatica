package com.smartfridge.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String nombre,
        @NotBlank @Email String correo,
        // Pequeña mejora añadida sobre lo pedido: el sistema legacy no
        // exigía ninguna longitud mínima de contraseña.
        @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres") String password
) {
}
