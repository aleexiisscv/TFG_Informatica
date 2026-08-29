package com.smartfridge.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registra {@link AsistenteProperties} como bean.
 *
 * <p>Se sigue el mismo patrón explícito que {@link GeminiConfig} y
 * {@link MqttConfig} en lugar de añadir {@code @ConfigurationPropertiesScan}
 * a la clase principal: con el scan, saber qué propiedades existen
 * obliga a rastrear anotaciones por todo el proyecto; con una clase de
 * configuración por área, el registro es explícito y localizable.</p>
 */
@Configuration
@EnableConfigurationProperties(AsistenteProperties.class)
public class AsistenteConfig {
}
