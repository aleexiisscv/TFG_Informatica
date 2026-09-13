package com.smartfridge.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import lombok.extern.slf4j.Slf4j;

/**
 * Firma y verificación de los tokens JWT (Fase 13).
 *
 * <h2>Simétrico (HS256) y no asimétrico (RS256)</h2>
 * Quien firma y quien verifica son el mismo servicio, así que no hay
 * ninguna ventaja en repartir un par de claves: RS256 solo compensa
 * cuando un tercero necesita validar tokens sin poder emitirlos. HS256
 * con una clave de 256 bits es igual de seguro para este caso y evita
 * gestionar un keystore.
 *
 * <h2>Qué pasa si no se aporta la clave</h2>
 * <b>No se usa una clave por defecto escrita en el código.</b> Una clave
 * de firma versionada en Git es una clave pública: cualquiera que lea el
 * repositorio puede emitir tokens válidos. Ante su ausencia caben dos
 * salidas, y ninguna es obvia:
 * <ul>
 *   <li>Fallar al arrancar. Es lo correcto en producción, pero deja al
 *       proyecto sin arrancar en mitad de una demostración por una
 *       variable de entorno olvidada.</li>
 *   <li>Generar una aleatoria en memoria. Arranca siempre, pero los
 *       tokens dejan de ser válidos en cada reinicio.</li>
 * </ul>
 * Se elige la segunda con un aviso muy visible en el log: el fallo
 * resultante ("me ha caducado la sesión al reiniciar") es evidente y se
 * diagnostica solo, mientras que una clave compartida y filtrada no da
 * ningún síntoma hasta que alguien la aprovecha.
 */
@Configuration
@Slf4j
@EnableConfigurationProperties(SeguridadProperties.class)
public class JwtConfig {

    /** 32 bytes = 256 bits, el mínimo que exige HS256. */
    private static final int LONGITUD_MINIMA_BYTES = 32;

    @Bean
    public SecretKey jwtSecretKey(SeguridadProperties propiedades) {
        String secreto = propiedades.jwtSecret();

        if (secreto == null || secreto.isBlank()) {
            log.warn("""
                    ================================================================
                     No se ha configurado smartfridge.seguridad.jwt-secret
                     (variable de entorno JWT_SECRET).
                     Se genera una clave ALEATORIA para esta ejecución: los tokens
                     emitidos dejarán de ser válidos en cuanto se reinicie el
                     backend y habrá que volver a iniciar sesión en la app.
                     Define JWT_SECRET (32+ caracteres) para un entorno estable.
                    ================================================================""");
            byte[] aleatoria = new byte[LONGITUD_MINIMA_BYTES];
            new SecureRandom().nextBytes(aleatoria);
            return new SecretKeySpec(aleatoria, "HmacSHA256");
        }

        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < LONGITUD_MINIMA_BYTES) {
            // Aquí sí se falla al arrancar: una clave corta NO es un
            // descuido de configuración, es una firma débil. Arrancar
            // igualmente daría una falsa sensación de seguridad.
            throw new IllegalStateException(
                    "smartfridge.seguridad.jwt-secret debe tener al menos " + LONGITUD_MINIMA_BYTES
                            + " caracteres para HS256 (tiene " + bytes.length + ")");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey clave) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey clave) {
        // macAlgorithm explícito: sin fijarlo, el decodificador aceptaría
        // cualquier algoritmo HMAC que declare el token. Aceptar lo que
        // proponga quien envía el token es justamente el patrón que
        // habilita los ataques de confusión de algoritmo.
        return NimbusJwtDecoder.withSecretKey(clave)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
