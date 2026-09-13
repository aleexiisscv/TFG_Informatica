package com.smartfridge.gemini;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saneado de las imágenes que llegan de un cliente antes de reenviarlas
 * a Gemini.
 *
 * <p>Nace en la Fase 12 de extraer dos métodos que ya existían dentro de
 * {@code VisionServiceImpl}. Al aparecer un segundo punto de entrada de
 * imágenes —el chat multimodal del asistente— duplicarlos habría
 * significado que una foto subida por la ESP32-CAM y una foto adjuntada
 * en el chat se validasen con reglas distintas, que es exactamente el
 * tipo de divergencia que nadie detecta hasta que falla en producción.</p>
 */
public final class ImagenEntrante {

    private static final Logger log = LoggerFactory.getLogger(ImagenEntrante.class);

    private static final String MIME_POR_DEFECTO = "image/jpeg";
    private static final String PREFIJO_DATA_URL = "base64,";

    private ImagenEntrante() {
        // Clase de utilidad
    }

    /**
     * Gemini solo acepta un conjunto cerrado de MIME types de imagen. Si
     * el cliente no etiqueta bien la imagen (Postman, la ESP32-CAM, o un
     * navegador que manda {@code application/octet-stream}), se fuerza a
     * JPEG en vez de reenviar un tipo inválido que Gemini rechazaría con
     * un 400 difícil de relacionar con la causa real.
     */
    public static String normalizarMime(String mimeType) {
        String normalizado = mimeType == null ? "" : mimeType.trim().toLowerCase();
        return switch (normalizado) {
            case "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif" -> normalizado;
            default -> {
                log.warn("Content-Type de imagen no soportado ('{}'); se fuerza a {}.",
                        mimeType, MIME_POR_DEFECTO);
                yield MIME_POR_DEFECTO;
            }
        };
    }

    /**
     * Retira el prefijo de <i>data URL</i> si el cliente lo ha incluido.
     *
     * <p>Un navegador que use {@code FileReader.readAsDataURL()} produce
     * {@code "data:image/jpeg;base64,/9j/4AAQ..."}. Ese prefijo NO forma
     * parte del Base64 y Gemini responde 400 si le llega. Es un error de
     * cliente tan habitual —y tan barato de absorber— que tiene más
     * sentido tolerarlo aquí que devolver un error que el desarrollador
     * del cliente tardará una tarde en entender.</p>
     *
     * <p>También se eliminan espacios y saltos de línea: algunos
     * codificadores generan Base64 en líneas de 76 caracteres (MIME),
     * que Gemini tampoco acepta.</p>
     */
    public static String limpiarBase64(String base64) {
        if (base64 == null) {
            return null;
        }
        String limpio = base64.trim();
        int marca = limpio.indexOf(PREFIJO_DATA_URL);
        if (limpio.startsWith("data:") && marca > 0) {
            limpio = limpio.substring(marca + PREFIJO_DATA_URL.length());
        }
        return limpio.replaceAll("\\s", "");
    }

    /**
     * Intenta deducir el MIME a partir de los primeros bytes del Base64
     * cuando el cliente no lo declara. Los "números mágicos" de cada
     * formato producen prefijos constantes al codificar en Base64, así
     * que basta con mirar el principio de la cadena sin decodificarla
     * entera —lo que en una foto de varios MB no es un detalle menor—.
     *
     * @return el MIME deducido, o {@code null} si no se reconoce
     */
    public static String deducirMime(String base64Limpio) {
        if (base64Limpio == null || base64Limpio.length() < 8) {
            return null;
        }
        String cabecera = base64Limpio.substring(0, 8);
        if (cabecera.startsWith("/9j/")) {
            return "image/jpeg";
        }
        if (cabecera.startsWith("iVBORw0")) {
            return "image/png";
        }
        if (cabecera.startsWith("UklGR")) {
            return "image/webp";
        }
        return null;
    }
}
