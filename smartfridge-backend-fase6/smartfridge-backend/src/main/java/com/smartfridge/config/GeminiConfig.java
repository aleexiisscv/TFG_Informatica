package com.smartfridge.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Fabrica el {@link RestClient} usado para hablar con la API REST de
 * Gemini, como Bean de Spring.
 *
 * Misma separación de responsabilidades que {@link MqttConfig}: esta
 * clase solo CONSTRUYE el cliente HTTP (baseUrl); la lógica de qué
 * payload enviar y cómo interpretar la respuesta de Gemini vive en
 * {@code VisionServiceImpl}, no aquí.
 *
 * Se usa {@code RestClient} — API síncrona nativa de Spring Framework
 * 6.1 / Spring Boot 3.2+, ya incluida en spring-boot-starter-web sin
 * necesidad de añadir ninguna dependencia nueva al pom.xml — en lugar de
 * {@code RestTemplate} (en modo mantenimiento desde Spring 5, sin nuevas
 * funcionalidades) o {@code WebClient} (pensado para flujos reactivos
 * que este backend no usa en ningún otro punto de la arquitectura). Es
 * la opción moderna recomendada por el propio equipo de Spring para
 * llamadas HTTP salientes síncronas.
 */
@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiConfig {

    @Bean
    public RestClient geminiRestClient(GeminiProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.apiBaseUrl())
                .build();
    }
}
