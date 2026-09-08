package com.smartfridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros de seguridad, bajo el prefijo "smartfridge.seguridad".
 *
 * @param jwtSecret           clave HMAC para firmar y verificar los tokens.
 *                            Mínimo 32 bytes (256 bits) para HS256. Se
 *                            resuelve desde la variable de entorno
 *                            {@code JWT_SECRET}; si no se aporta,
 *                            {@code JwtConfig} genera una aleatoria al
 *                            arrancar (ver allí el porqué).
 * @param jwtExpiracionMinutos vigencia del token
 * @param emisor              claim {@code iss}
 * @param claveDispositivo    secreto compartido con la ESP32-CAM para que
 *                            pueda subir fotos sin ser un usuario. Vacío
 *                            = ningún dispositivo puede entrar
 */
@ConfigurationProperties(prefix = "smartfridge.seguridad")
public record SeguridadProperties(
        String jwtSecret,
        Integer jwtExpiracionMinutos,
        String emisor,
        String claveDispositivo
) {

    public SeguridadProperties {
        jwtExpiracionMinutos = jwtExpiracionMinutos != null ? jwtExpiracionMinutos : 720; // 12 h
        emisor = (emisor == null || emisor.isBlank()) ? "smartfridge-backend" : emisor;
    }

    public boolean hayClaveDeDispositivo() {
        return claveDispositivo != null && !claveDispositivo.isBlank();
    }
}
