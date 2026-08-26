package com.smartfridge.dto;

/**
 * Deliberadamente NO incluye passwordHash: aunque ya no es la
 * contraseña en texto plano, un hash filtrado sigue siendo un activo
 * atacable (fuerza bruta offline). La regla general es la misma que
 * justifica los DTOs en el resto de la API: el cliente recibe
 * exactamente los campos que necesita, ni uno más.
 */
public record AuthResponse(
        Long id,
        String nombre,
        String correo
) {
}
