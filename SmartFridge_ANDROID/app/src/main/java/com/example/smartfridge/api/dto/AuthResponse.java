package com.example.smartfridge.api.dto;

/**
 * Respuesta de /api/auth/login y /api/auth/registro.
 *
 * <p>Fase 13: incorpora el JWT. El backend lo devuelve en el mismo paso
 * del login porque autenticarse y obtener la credencial de sesión son,
 * desde el punto de vista del cliente, una sola acción.</p>
 */
public class AuthResponse {
    public Long id;
    public String nombre;
    public String correo;

    /** JWT firmado, a enviar como {@code Authorization: Bearer <token>}. */
    public String token;

    /** Siempre "Bearer". Llega explícito para no codificar la suposición. */
    public String tipoToken;

    /** Vigencia en segundos, para poder anticiparse en vez de recibir un 401. */
    public Long expiraEnSegundos;
}
