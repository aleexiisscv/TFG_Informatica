package com.smartfridge.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.smartfridge.config.SeguridadProperties;
import com.smartfridge.model.Usuario;

import lombok.RequiredArgsConstructor;

/**
 * Emisión de tokens JWT (Fase 13).
 *
 * <p>Se separa de {@code UsuarioService} a propósito: autenticar —
 * comprobar que una contraseña coincide con su hash— y emitir una
 * credencial de sesión son responsabilidades distintas. Si mañana se
 * añade login con Google, cambia quién autentica pero no quién emite el
 * token; y si se pasa de JWT a sesiones opacas, al revés.</p>
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    /**
     * Token emitido, con lo que el cliente necesita saber para usarlo.
     *
     * @param valor            el JWT
     * @param tipo             siempre "Bearer": es el esquema que la app
     *                         debe poner en la cabecera Authorization
     * @param expiraEnSegundos vigencia restante, para que el cliente
     *                         pueda anticiparse en vez de descubrirlo con
     *                         un 401
     */
    public record Token(String valor, String tipo, long expiraEnSegundos) {
    }

    private final JwtEncoder jwtEncoder;
    private final SeguridadProperties propiedades;

    public Token generar(Usuario usuario) {
        Instant ahora = Instant.now();
        long duracionSegundos = propiedades.jwtExpiracionMinutos() * 60L;

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(propiedades.emisor())
                .issuedAt(ahora)
                .expiresAt(ahora.plus(propiedades.jwtExpiracionMinutos(), ChronoUnit.MINUTES))
                // "sub" es el identificador estable del usuario, no su
                // correo: si algún día se permite cambiar de correo, los
                // tokens ya emitidos seguirían apuntando a la persona
                // correcta.
                .subject(String.valueOf(usuario.getId()))
                .claim("correo", usuario.getCorreo())
                .claim("nombre", usuario.getNombre())
                .build();

        // La cabecera DEBE declarar HS256 explicitamente. Sin ella,
        // NimbusJwtEncoder asume RS256 por defecto y el selector de
        // claves no encuentra ninguna RSA dentro del ImmutableSecret
        // (que solo contiene una clave simetrica), fallando con
        // "Failed to select a JWK signing key". El decodificador ya
        // fijaba HS256; el codificador tambien debe hacerlo.
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();

        String valor = jwtEncoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
        return new Token(valor, "Bearer", duracionSegundos);
    }
}
