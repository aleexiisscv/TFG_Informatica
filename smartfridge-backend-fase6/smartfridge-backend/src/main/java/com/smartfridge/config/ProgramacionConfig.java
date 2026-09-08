package com.smartfridge.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita las dos capacidades de ejecución en segundo plano que
 * introduce la Fase 13:
 *
 * <ul>
 *   <li>{@code @EnableScheduling} para el motor de caducidades
 *       ({@code InventarioServiceImpl.revisarCaducidades}).</li>
 *   <li>{@code @EnableAsync} para que la captura automática de la cámara
 *       no bloquee el hilo que entrega los mensajes MQTT. Sin esto, una
 *       ESP32-CAM lenta o apagada dejaría al cliente Paho sin procesar
 *       lecturas de temperatura durante todo el timeout.</li>
 * </ul>
 *
 * <p>Se agrupan en una clase propia en lugar de anotar la clase
 * principal: mantiene {@code SmartfridgeBackendApplication} como un
 * simple punto de entrada y deja localizable, en un solo sitio, qué hace
 * esta aplicación por su cuenta sin que nadie se lo pida — que es
 * exactamente la pregunta que uno se hace al heredar un proyecto.</p>
 */
@Configuration
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties(CamaraProperties.class)
public class ProgramacionConfig {
}
