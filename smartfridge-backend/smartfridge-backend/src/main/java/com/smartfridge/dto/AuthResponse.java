package com.smartfridge.dto;

/**
 * Respuesta de /api/auth/login y /api/auth/registro.
 *
 * <p>Deliberadamente NO incluye passwordHash: aunque ya no es la
 * contraseña en texto plano, un hash filtrado sigue siendo un activo
 * atacable (fuerza bruta offline). La regla general es la misma que
 * justifica los DTOs en el resto de la API: el cliente recibe
 * exactamente los campos que necesita, ni uno más.</p>
 *
 * <p>Fase 13: incorpora el token JWT. Se devuelve en el mismo momento
 * del login y no en un segundo endpoint porque autenticarse y obtener
 * la credencial de sesión son, desde el punto de vista del cliente, un
 * solo paso; separarlos añadiría una ida y vuelta sin aportar nada.</p>
 *
 * @param token            JWT firmado, a enviar como
 *                         {@code Authorization: Bearer <token>}
 * @param tipoToken        siempre "Bearer"; se envía explícitamente para
 *                         que el cliente no tenga que codificar esa
 *                         suposición
 * @param expiraEnSegundos vigencia, para que la app pueda anticiparse en
 *                         vez de enterarse con un 401
 */
public record AuthResponse(
        Long id,
        String nombre,
        String correo,
        String token,
        String tipoToken,
        Long expiraEnSegundos
) {
}
