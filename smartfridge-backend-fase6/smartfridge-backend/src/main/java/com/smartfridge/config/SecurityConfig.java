package com.smartfridge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad de esta iteración: define el
 * {@link PasswordEncoder} (BCrypt) que sustituirá a la comparación de
 * contraseñas en texto plano del sistema legacy, y deja /api/** abierto
 * a propósito para poder desarrollar y probar los controladores REST
 * sin bloquear el trabajo de la app Android.
 *
 * ATENCIÓN — TODO Fase final: este permitAll() es temporal. Antes de
 * dar el TFG por cerrado hay que sustituirlo por autenticación real
 * (JWT es la opción natural para una API stateless consumida por una
 * app móvil) y restringir al menos POST /api/productos y cualquier
 * endpoint de escritura futuro a usuarios autenticados.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protege formularios HTML que envían cookies de sesión
                // automáticamente con cada petición. Esta API es stateless
                // (JSON puro, sin sesión de servidor ni cookies), así que ese
                // vector de ataque no aplica; desactivarlo es la práctica
                // estándar de Spring Security para APIs REST sin estado.
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/**").permitAll() // TEMPORAL — ver TODO de la clase
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
