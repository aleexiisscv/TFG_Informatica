package com.smartfridge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;

/**
 * Configuración de seguridad.
 *
 * <h2>Fase 13: se cierran los endpoints que cuestan dinero</h2>
 * Hasta ahora {@code /api/**} estaba abierto entero, con un TODO. Se
 * cierran los dos que consumen cuota de pago de Google:
 * <ul>
 *   <li>{@code /api/vision/**} — cada llamada es una inferencia
 *       multimodal.</li>
 *   <li>{@code /api/asistente/**} — cada llamada es una generación con
 *       todo el contexto del frigorífico inyectado.</li>
 * </ul>
 *
 * <p>El criterio no es "cerrar lo que parezca sensible" sino <b>cerrar
 * primero lo que un tercero puede convertir en una factura</b>. Los
 * endpoints de datos exponen el inventario de una nevera; los de IA
 * exponen una tarjeta de crédito.</p>
 *
 * <h2>Lo que sigue abierto, y por qué se dice en voz alta</h2>
 * {@code /api/inventario}, {@code /api/sensores}, {@code /api/registros}
 * y {@code /api/productos} siguen siendo públicos. Es una decisión
 * consciente de alcance para esta fase, no un olvido: cerrarlos es
 * cambiar {@code permitAll()} por {@code authenticated()} en la línea
 * marcada más abajo, pero obliga a que la app envíe el token en TODAS
 * sus pantallas y a que cualquier prueba con Postman lo incluya. Queda
 * como el último paso natural antes de dar el proyecto por cerrado.
 *
 * <h2>Dos formas de autenticarse, con alcances distintos</h2>
 * <ul>
 *   <li><b>Personas</b> → JWT emitido en {@code /api/auth/login}.</li>
 *   <li><b>La ESP32-CAM</b> → cabecera {@code X-Device-Key}, que solo
 *       abre {@code /api/vision/**} (ver
 *       {@link DeviceKeyAuthenticationFilter}).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SeguridadProperties seguridadProperties;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protege formularios HTML que envían cookies de sesión
                // automáticamente. Esta API es stateless (JSON puro, sin
                // sesión ni cookies), así que ese vector no aplica.
                .csrf(AbstractHttpConfigurer::disable)

                // STATELESS: ni se crea ni se consulta HttpSession. Con JWT,
                // toda la identidad viaja en cada petición. Sin esta línea,
                // Spring Security crearía una sesión de servidor por cliente
                // —justo el estado que el diseño evita en todas las capas.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Registro y login son públicos por necesidad: son
                        // los endpoints que permiten obtener acceso, no
                        // pueden exigir estar ya autenticado.
                        .requestMatchers("/api/auth/**").permitAll()

                        // Preflight de CORS: el navegador lo envía sin
                        // credenciales, así que rechazarlo rompería
                        // cualquier cliente web futuro.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // --- Endpoints de IA: cuota de pago ---
                        .requestMatchers("/api/vision/**", "/api/asistente/**").authenticated()

                        // --- Resto de la API: abierto POR AHORA ---
                        // Cambiar a .authenticated() para cerrar el sistema
                        // por completo (ver nota en el javadoc de la clase).
                        .requestMatchers("/api/**").permitAll()

                        .anyRequest().authenticated())

                // Valida "Authorization: Bearer ..." con el JwtDecoder de
                // JwtConfig. Un token con firma inválida, caducado o de otro
                // emisor produce 401 antes de llegar al controlador.
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))

                // El filtro de dispositivo va ANTES para que la ESP32-CAM
                // quede autenticada aunque no traiga cabecera Authorization.
                // Se instancia aquí y no se inyecta como bean: ver la
                // nota sobre el doble registro en DeviceKeyAuthenticationFilter.
                .addFilterBefore(new DeviceKeyAuthenticationFilter(seguridadProperties),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
