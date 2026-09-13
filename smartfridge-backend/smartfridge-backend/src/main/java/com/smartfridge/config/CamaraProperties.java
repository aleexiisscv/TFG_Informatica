package com.smartfridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexión con la ESP32-CAM, bajo el prefijo "smartfridge.camara".
 *
 * @param disparoAutomatico  interruptor maestro de la orquestación
 *                           cámara-puerta. <b>Apagado por defecto</b>
 *                           (ver {@code CapturaAutomaticaService} para
 *                           el motivo).
 * @param baseUrl            raíz HTTP de la placa, p. ej.
 *                           {@code http://192.168.0.50}
 * @param rutaCaptura        endpoint que devuelve el JPEG. {@code /capture}
 *                           es el que expone el servidor de ejemplo del
 *                           componente esp32-camera.
 * @param intervaloMinimoMs  antirrebote: tiempo mínimo entre dos capturas
 * @param timeoutConexionMs  tope para ABRIR la conexión con la placa
 * @param timeoutLecturaMs   tope para recibir el JPEG completo
 */
@ConfigurationProperties(prefix = "smartfridge.camara")
public record CamaraProperties(
        Boolean disparoAutomatico,
        String baseUrl,
        String rutaCaptura,
        Long intervaloMinimoMs,
        Integer timeoutConexionMs,
        Integer timeoutLecturaMs
) {

    /**
     * Valores por defecto en el constructor compacto: la aplicación
     * arranca igual aunque application.yml no declare la sección, y lo
     * hace con el disparo APAGADO. Un valor por defecto que activase una
     * automatización sería una sorpresa desagradable al desplegar.
     */
    public CamaraProperties {
        disparoAutomatico = disparoAutomatico != null && disparoAutomatico;
        rutaCaptura = (rutaCaptura == null || rutaCaptura.isBlank()) ? "/capture" : rutaCaptura;
        intervaloMinimoMs = intervaloMinimoMs != null ? intervaloMinimoMs : 15_000L;
        timeoutConexionMs = timeoutConexionMs != null ? timeoutConexionMs : 3_000;
        timeoutLecturaMs = timeoutLecturaMs != null ? timeoutLecturaMs : 10_000;
    }

    public boolean activo() {
        return Boolean.TRUE.equals(disparoAutomatico) && baseUrl != null && !baseUrl.isBlank();
    }
}
