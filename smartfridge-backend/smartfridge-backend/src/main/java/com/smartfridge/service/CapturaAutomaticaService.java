package com.smartfridge.service;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.smartfridge.config.CamaraProperties;
import com.smartfridge.evento.PuertaCerradaEvent;
import com.smartfridge.exception.ProductoNoEncontradoException;
import com.smartfridge.model.Inventario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orquestación cámara-puerta: al cerrarse la puerta, descarga una
 * fotografía de la ESP32-CAM y la pasa por el flujo de visión.
 *
 * <h2>ESTADO: DESACTIVADO POR DEFECTO</h2>
 * Toda la clase está detrás de
 * {@code smartfridge.camara.disparo-automatico}, que vale {@code false}.
 * Con la configuración de serie <b>este bean ni siquiera se crea</b>: el
 * sistema funciona exactamente como antes de la Fase 13, con la
 * ESP32-CAM tomando su fotografía al arrancar y subiéndola ella misma.
 *
 * <p>El motivo es de hardware, no de software: el sensor magnético de
 * puerta produce falsos contactos, y un disparador conectado a un sensor
 * que rebota dispararía capturas en ráfaga. Se deja implementado y
 * probado, pero apagado, para activarlo cuando el sensor sea fiable.</p>
 *
 * <p><b>Por qué un interruptor de configuración y no código comentado.</b>
 * El código comentado no compila, no se revisa y se pudre en silencio:
 * al descomentarlo meses después casi nunca funciona a la primera. Un
 * flag mantiene la ruta viva —compila, se analiza, se puede probar
 * poniendo la propiedad a {@code true}— sin ejecutarse. Activarlo es
 * cambiar una línea de {@code application.yml}, sin recompilar.</p>
 *
 * <h2>Por qué el servidor descarga la foto, y no la cámara la sube</h2>
 * La alternativa era que el backend ordenase disparar y la placa subiera
 * el JPEG a {@code /api/vision/analizar}. Se descartó porque ese
 * endpoint queda cerrado con JWT en esta misma fase: obligaría a dejarle
 * una puerta abierta. Descargando la imagen, la comunicación es
 * saliente, la placa no necesita credenciales del backend y el endpoint
 * puede cerrarse de verdad.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "smartfridge.camara", name = "disparo-automatico", havingValue = "true")
public class CapturaAutomaticaService {

    private final RestClient camaraRestClient;
    private final CamaraProperties propiedades;
    private final VisionService visionService;
    private final InventarioService inventarioService;

    /** Momento de la última captura, para el antirrebote. */
    private final AtomicLong ultimaCaptura = new AtomicLong(0L);

    /**
     * {@code @Async}: la captura NO puede correr en el hilo que entrega
     * los mensajes MQTT. Ese hilo es único; bloquearlo diez segundos
     * esperando a una placa lenta dejaría sin procesar las lecturas de
     * temperatura que lleguen mientras tanto.
     *
     * <p>{@code @EventListener} y no una llamada directa desde el router:
     * ver la justificación en {@link PuertaCerradaEvent}.</p>
     */
    @Async
    @EventListener
    public void alCerrarseLaPuerta(PuertaCerradaEvent evento) {
        if (!debeCapturar()) {
            return;
        }
        log.info("Puerta cerrada a las {}: solicitando fotografía a la ESP32-CAM", evento.momento());

        byte[] jpeg = descargarFoto();
        if (jpeg == null || jpeg.length == 0) {
            return;
        }
        procesar(jpeg);
    }

    /**
     * Antirrebote. Un sensor magnético que rebota puede publicar varios
     * "Puerta cerrada" en menos de un segundo, y cada uno costaría una
     * llamada a la API de visión — es decir, cuota de pago. El intervalo
     * mínimo convierte una ráfaga de rebotes en una sola captura.
     *
     * <p>Se usa {@code compareAndSet} sobre un {@link AtomicLong} en vez
     * de {@code synchronized}: los eventos llegan en hilos del pool de
     * {@code @Async} y pueden solaparse. Con CAS, de dos capturas
     * simultáneas solo una gana la carrera; con una comprobación normal,
     * ambas pasarían.</p>
     */
    private boolean debeCapturar() {
        long ahora = System.currentTimeMillis();
        long anterior = ultimaCaptura.get();
        if (ahora - anterior < propiedades.intervaloMinimoMs()) {
            log.debug("Captura descartada por antirrebote ({} ms desde la anterior)", ahora - anterior);
            return false;
        }
        return ultimaCaptura.compareAndSet(anterior, ahora);
    }

    private byte[] descargarFoto() {
        try {
            byte[] jpeg = camaraRestClient.get()
                    .uri(propiedades.rutaCaptura())
                    .accept(MediaType.IMAGE_JPEG, MediaType.ALL)
                    .retrieve()
                    .body(byte[].class);

            if (jpeg == null || jpeg.length == 0) {
                log.warn("La ESP32-CAM respondió sin contenido a {}", propiedades.rutaCaptura());
                return null;
            }
            log.info("Fotografía recibida de la ESP32-CAM ({} bytes)", jpeg.length);
            return jpeg;

        } catch (RestClientException e) {
            // Placa apagada, reiniciándose, IP cambiada o WiFi floja. Es
            // el caso habitual, no el excepcional: se registra y se sigue.
            // Que la cámara no responda no debe afectar a nada más.
            log.warn("No se pudo obtener la fotografía de la ESP32-CAM ({}{}): {}",
                    propiedades.baseUrl(), propiedades.rutaCaptura(), e.getMessage());
            return null;
        }
    }

    /**
     * Mismo flujo que {@code VisionController}: identificar y dar de alta.
     *
     * <p>Se repite la orquestación en vez de llamar al controlador porque
     * un controlador es capa de transporte: invocarlo desde un servicio
     * invertiría las dependencias. Son cuatro líneas; extraerlas a un
     * tercer servicio compartido sería la mejora natural si aparece un
     * tercer origen de imágenes.</p>
     */
    private void procesar(byte[] jpeg) {
        String identificador = visionService.identificarProducto(jpeg, MediaType.IMAGE_JPEG_VALUE);

        if ("DESCONOCIDO".equals(identificador)) {
            // No es un error: el usuario pudo cerrar la puerta sin meter
            // nada, o el producto no está en el catálogo. Dar de alta algo
            // "parecido" ensuciaría el inventario sin que nadie se entere.
            log.info("La fotografía automática no corresponde a ningún producto del catálogo");
            return;
        }

        try {
            Inventario unidad = inventarioService.anadirProducto(identificador);
            log.info("Alta automática por visión: '{}' (caduca {})",
                    unidad.getProducto().getNombre(), unidad.getFechaCaducidad());
        } catch (ProductoNoEncontradoException e) {
            // Con el enum de la Fase 13 esto no debería ocurrir: el
            // identificador sale del propio catálogo. Se captura por si
            // el producto se borrase entre la construcción del enum y el
            // alta — una carrera improbable pero real.
            log.warn("El identificador '{}' ya no existe en el catálogo: {}",
                    identificador, e.getMessage());
        }
    }
}
