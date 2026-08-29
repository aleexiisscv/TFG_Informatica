package com.smartfridge.service;

/**
 * Puerto de entrada al modelo de visión por computador (Gemini).
 *
 * Deliberadamente NO depende de {@code MultipartFile} ni de ningún tipo
 * de Spring Web: recibe los bytes de la imagen ya extraídos por el
 * controlador. Es la misma razón por la que el resto de Services del
 * proyecto (InventarioService, SensorService...) no conocen HttpStatus
 * ni ResponseEntity — la capa de servicio no debe acoplarse al
 * transporte (inversión de dependencias). Esto además permite testear
 * VisionServiceImpl con un array de bytes en memoria, sin tener que
 * construir un MultipartFile falso en el test.
 */
public interface VisionService {

    /**
     * Envía la imagen a Gemini y devuelve el identificador de producto en
     * snake_case (p. ej. "brick_leche"), o el literal "DESCONOCIDO" si el
     * modelo no reconoce ningún producto en la imagen.
     *
     * @param imagenBytes contenido binario de la fotografía capturada por la ESP32-CAM
     * @param mimeType    tipo MIME de la imagen (p. ej. "image/jpeg")
     */
    String identificarProducto(byte[] imagenBytes, String mimeType);
}
