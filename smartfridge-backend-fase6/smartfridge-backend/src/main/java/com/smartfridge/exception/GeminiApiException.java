package com.smartfridge.exception;

/**
 * Se lanza cuando la llamada a la API de Gemini falla como servicio: red
 * caída, clave de API inválida, cuota agotada, modelo no disponible tras
 * agotar también el de respaldo, o respuesta 200 sin candidatos (bloqueo
 * por los filtros de seguridad de Google).
 *
 * Ver comentario en {@link ProductoNoIdentificadoException} para la
 * distinción deliberada entre ambas excepciones.
 */
public class GeminiApiException extends RuntimeException {

    public GeminiApiException(String mensaje) {
        super(mensaje);
    }

    public GeminiApiException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
