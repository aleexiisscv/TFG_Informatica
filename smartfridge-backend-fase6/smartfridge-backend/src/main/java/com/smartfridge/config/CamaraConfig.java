package com.smartfridge.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Cliente HTTP hacia la ESP32-CAM.
 *
 * <p>{@code @ConditionalOnProperty} hace que esta configuración —y por
 * tanto el bean— <b>no exista</b> mientras el disparo automático esté
 * apagado. No es un detalle estético: significa que con la
 * configuración por defecto no se instancia ningún cliente HTTP hacia
 * la placa, no se resuelve ninguna IP y no hay forma de que la
 * funcionalidad se active por accidente. El sistema se comporta
 * exactamente igual que antes de la Fase 13.</p>
 *
 * <p>Los timeouts son cortos a propósito. Una ESP32-CAM apagada,
 * reiniciándose o con la WiFi floja es el caso <i>habitual</i>, no el
 * excepcional; sin tope, cada cierre de puerta dejaría un hilo colgado
 * esperando indefinidamente.</p>
 */
@Configuration
@Slf4j
@ConditionalOnProperty(prefix = "smartfridge.camara", name = "disparo-automatico", havingValue = "true")
public class CamaraConfig {

    @Bean
    public RestClient camaraRestClient(CamaraProperties propiedades) {
        log.info("Disparo automático de cámara ACTIVADO. Base URL de la ESP32-CAM: {}",
                propiedades.baseUrl());

        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofMillis(propiedades.timeoutConexionMs()));
        fabrica.setReadTimeout(Duration.ofMillis(propiedades.timeoutLecturaMs()));

        return RestClient.builder()
                .baseUrl(propiedades.baseUrl())
                .requestFactory(fabrica)
                .build();
    }
}
