package com.smartfridge.exception;

/**
 * Se lanza cuando Gemini responde correctamente pero no reconoce ningún
 * producto en la fotografía (identificador "DESCONOCIDO").
 *
 * Es el equivalente, en el flujo de visión, a una lectura RFID que no
 * emite ningún tag: no es un fallo del sistema, es una foto sin producto
 * claro (mal encuadre, objeto no soportado por el catálogo, mano
 * tapando el producto...). Por eso se modela como una situación de
 * negocio (422 UNPROCESSABLE_ENTITY) y no como un error de servidor,
 * y se distingue deliberadamente de {@link GeminiApiException}: esta
 * excepción significa "Gemini ha respondido correctamente y no ha visto
 * nada identificable"; GeminiApiException significa "Gemini ha fallado
 * como servicio". La app Android necesita poder distinguir ambos casos
 * para decidir si tiene sentido reintentar la foto o no.
 */
public class ProductoNoIdentificadoException extends RuntimeException {

    public ProductoNoIdentificadoException() {
        super("Gemini no ha reconocido ningún producto en la imagen enviada");
    }
}
