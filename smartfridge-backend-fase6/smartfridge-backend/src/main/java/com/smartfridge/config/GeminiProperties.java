package com.smartfridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de conexión a la API REST de Gemini, mapeadas desde el
 * prefijo "smartfridge.gemini" de application.yml.
 *
 * Mismo patrón que {@link MqttProperties}: un record inmutable con
 * constructor binding en vez de una clase con getters/setters mutables,
 * ya soportado de forma nativa por Spring Boot 3.x. La api-key NUNCA se
 * hardcodea aquí ni en application.yml: se resuelve en tiempo de
 * arranque desde la variable de entorno GEMINI_API_KEY (ver comentario
 * de seguridad junto a la propiedad en application.yml), de modo que la
 * clave real nunca llega a versionarse en el repositorio Git.
 */
@ConfigurationProperties(prefix = "smartfridge.gemini")
public record GeminiProperties(
        String apiKey,
        String model,
        String fallbackModel,
        String apiBaseUrl
) {
}
