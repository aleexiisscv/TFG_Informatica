package com.smartfridge.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

/**
 * Traduce las excepciones de dominio y de validación a respuestas HTTP
 * consistentes en toda la API. Sustituye al patrón legacy de
 * {@code DatabaseServlet}, que en caso de error devolvía un texto plano
 * suelto ("Failed to obtain a connection...") con un status 200 —
 * imposible de manejar programáticamente desde la app Android.
 *
 * NOTA Fase 9: se añaden aquí los dos manejadores de
 * {@link ProductoNoIdentificadoException} y {@link GeminiApiException}
 * en vez de capturarlas con try/catch dentro de VisionController — es
 * el mismo criterio ya aplicado a ProductoNoEncontradoException desde la
 * Fase 5: centralizar el mapeo excepción → HTTP aquí mantiene los
 * controladores finos (SRP) y la respuesta de error consistente en toda
 * la API, en vez de reinventar el formato de error controlador a
 * controlador.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handleProductoNoEncontrado(ProductoNoEncontradoException e) {
        return construirRespuesta(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InventarioVacioException.class)
    public ResponseEntity<Map<String, Object>> handleInventarioVacio(InventarioVacioException e) {
        return construirRespuesta(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<Map<String, Object>> handleCredencialesInvalidas(CredencialesInvalidasException e) {
        return construirRespuesta(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(CorreoYaRegistradoException.class)
    public ResponseEntity<Map<String, Object>> handleCorreoYaRegistrado(CorreoYaRegistradoException e) {
        return construirRespuesta(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * Fase 9: Gemini ha respondido correctamente pero no ha reconocido
     * ningún producto en la imagen ("DESCONOCIDO"). Situación de negocio,
     * no un error de servidor.
     */
    @ExceptionHandler(ProductoNoIdentificadoException.class)
    public ResponseEntity<Map<String, Object>> handleProductoNoIdentificado(ProductoNoIdentificadoException e) {
        return construirRespuesta(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    /**
     * Fase 9: la API de Gemini ha fallado como servicio (red, cuota,
     * modelo no disponible ni siquiera con el de respaldo...). Se
     * registra con nivel ERROR porque, a diferencia de un 404/422, esto
     * sí indica un problema operativo que conviene monitorizar; el
     * cliente recibe 502 BAD_GATEWAY, el código estándar para "el
     * servidor, actuando como pasarela, recibió una respuesta inválida
     * de un servicio upstream".
     */
    @ExceptionHandler(GeminiApiException.class)
    public ResponseEntity<Map<String, Object>> handleGeminiApiException(GeminiApiException e) {
        log.error("Fallo al invocar la API de Gemini", e);
        return construirRespuesta(HttpStatus.BAD_GATEWAY,
                "El servicio de reconocimiento de imagen no está disponible en este momento");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidacion(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return construirRespuesta(HttpStatus.BAD_REQUEST, detalle);
    }

    private ResponseEntity<Map<String, Object>> construirRespuesta(HttpStatus status, String mensaje) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", mensaje);
        return ResponseEntity.status(status).body(body);
    }
}
