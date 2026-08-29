package com.smartfridge.service;

import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;

import com.smartfridge.gemini.GeminiClient;
import com.smartfridge.gemini.GeminiMensaje;
import com.smartfridge.gemini.GeminiOpciones;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Identificación de producto por visión (Fase 9).
 *
 * <h2>Refactor de la Fase 11 — mismo comportamiento, menos código</h2>
 * Esta clase contenía, además de su prompt, TODO el cliente HTTP de
 * Gemini: construcción del payload, modelo de respaldo, thinkingConfig
 * por familia de modelo, detección de candidatos vacíos y logging con
 * redacción del Base64. Al añadir el asistente conversacional, esa
 * lógica pasó a {@link GeminiClient} para no duplicarla.
 *
 * <p>Lo que se conserva EXACTAMENTE igual, porque es política de este
 * caso de uso y no del transporte:</p>
 * <ul>
 *   <li>El system prompt (identificador snake_case o "DESCONOCIDO").</li>
 *   <li>{@code temperature = 0.0}: clasificar no es crear; ante la misma
 *       foto queremos siempre la misma etiqueta.</li>
 *   <li>{@code maxOutputTokens = 512}: la respuesta real son 3-6 tokens,
 *       pero los modelos "thinking" descuentan del mismo presupuesto su
 *       razonamiento interno, y ese gasto varía según lo ambigua que sea
 *       la foto. Quedarse corto no trunca la respuesta: devuelve un
 *       candidato VACÍO con finishReason=MAX_TOKENS. Es el bug que se
 *       depuró en la Fase 9 y por eso el margen es tan holgado.</li>
 *   <li>La normalización de MIME types.</li>
 * </ul>
 *
 * <p>El único cambio observable en el JSON saliente es que ahora el
 * único {@code content} lleva {@code "role": "user"} explícito. Era
 * opcional para un turno suelto y es obligatorio en conversaciones de
 * varios turnos; mandarlo siempre evita mantener dos caminos distintos y
 * es igualmente válido para Gemini.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VisionServiceImpl implements VisionService {

    private static final String SYSTEM_PROMPT = """
            Eres el cerebro de un frigorífico inteligente. En la imagen verás un producto de supermercado. \
            Debes identificarlo y devolver ÚNICAMENTE un identificador corto en formato snake_case \
            (ej. 'brick_leche', 'lata_coca_cola', 'queso_havarti'). \
            Si no ves ningún producto claro, devuelve 'DESCONOCIDO'.""";

    private static final String DESCONOCIDO = "DESCONOCIDO";

    /** Ver la nota sobre modelos "thinking" en el javadoc de la clase. */
    private static final GeminiOpciones OPCIONES = GeminiOpciones.de(0.0, 512);

    private final GeminiClient geminiClient;

    @Override
    public String identificarProducto(byte[] imagenBytes, String mimeType) {
        String base64 = Base64.getEncoder().encodeToString(imagenBytes);
        String mime = mimeNormalizado(mimeType);

        String textoBruto = geminiClient.generar(
                SYSTEM_PROMPT,
                List.of(GeminiMensaje.usuarioConImagen(
                        "Identifica el producto de esta fotografía.", mime, base64)),
                OPCIONES);

        return normalizar(textoBruto);
    }

    /**
     * Gemini solo acepta un conjunto cerrado de MIME types de imagen. Si el
     * cliente (Postman, o la ESP32-CAM) no etiqueta bien la parte
     * multipart, se fuerza a image/jpeg en vez de reenviar un tipo
     * inválido que Gemini rechazaría con un 400 confuso.
     */
    private String mimeNormalizado(String mimeType) {
        String normalizado = mimeType == null ? "" : mimeType.toLowerCase();
        return switch (normalizado) {
            case "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif" -> normalizado;
            default -> {
                log.warn("Content-Type de imagen no soportado ('{}'); se fuerza a image/jpeg. "
                        + "Revisa que el cliente envíe el Content-Type correcto en la parte multipart.", mimeType);
                yield "image/jpeg";
            }
        };
    }

    private String normalizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return DESCONOCIDO;
        }
        return texto.trim();
    }
}
