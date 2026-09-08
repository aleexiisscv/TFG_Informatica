package com.smartfridge.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Autenticación de DISPOSITIVO para la ESP32-CAM (Fase 13).
 *
 * <h2>Por qué no basta con JWT</h2>
 * El JWT identifica a una <b>persona</b> que ha iniciado sesión. La
 * ESP32-CAM no es una persona: no tiene teclado, no puede renovar un
 * token caducado ni volver a introducir una contraseña. Meterle
 * credenciales de usuario en el firmware sería peor que no cerrar el
 * endpoint, porque esas credenciales acabarían en el repositorio y
 * servirían para todo lo demás.
 *
 * <p>La solución estándar para un dispositivo desatendido es un secreto
 * propio, con alcance mínimo: esta clave <b>solo</b> abre
 * {@code /api/vision/**} y no da acceso a nada más. Si la placa se
 * pierde o el secreto se filtra, se rota una propiedad y ya está, sin
 * tocar ninguna cuenta de usuario.</p>
 *
 * <p>Si no se configura ninguna clave, el filtro no hace nada: el
 * endpoint queda accesible solo con JWT. No hay valor por defecto — una
 * clave por defecto es una puerta abierta con la llave puesta.</p>
 *
 * <p><b>Deliberadamente NO es un {@code @Component}.</b> Spring Boot
 * registra automáticamente en la cadena del servlet cualquier bean de
 * tipo {@code Filter}, de modo que este se ejecutaría dos veces: una
 * dentro de la cadena de Spring Security y otra fuera de ella, antes de
 * que exista contexto de seguridad. Se instancia a mano en
 * {@link SecurityConfig}, que es donde debe vivir.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class DeviceKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String CABECERA = "X-Device-Key";
    private static final String RUTA_PROTEGIDA = "/api/vision";

    private final SeguridadProperties propiedades;

    /**
     * El filtro solo mira las peticiones de visión. Aplicarlo a toda la
     * API permitiría que una clave de dispositivo abriese endpoints que
     * nunca se pensaron para él — exactamente el privilegio excesivo que
     * este mecanismo quiere evitar.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(RUTA_PROTEGIDA)
                || !propiedades.hayClaveDeDispositivo();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String recibida = request.getHeader(CABECERA);

        if (recibida != null && coincide(recibida, propiedades.claveDispositivo())
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            UsernamePasswordAuthenticationToken autenticacion = new UsernamePasswordAuthenticationToken(
                    "esp32-cam", null, List.of(new SimpleGrantedAuthority("ROLE_DISPOSITIVO")));
            SecurityContextHolder.getContext().setAuthentication(autenticacion);
            log.debug("Petición autenticada como dispositivo mediante {}", CABECERA);
        }

        chain.doFilter(request, response);
    }

    /**
     * Comparación en tiempo constante.
     *
     * <p>{@code String.equals} corta en cuanto encuentra el primer
     * carácter distinto, así que el tiempo de respuesta filtra
     * información sobre cuántos caracteres se acertaron. Con suficientes
     * intentos, eso permite reconstruir la clave carácter a carácter.
     * {@code MessageDigest.isEqual} recorre siempre ambos arrays
     * completos.</p>
     */
    private boolean coincide(String recibida, String esperada) {
        return MessageDigest.isEqual(
                recibida.getBytes(StandardCharsets.UTF_8),
                esperada.getBytes(StandardCharsets.UTF_8));
    }
}
