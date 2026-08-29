package com.smartfridge.gemini;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfridge.config.GeminiProperties;
import com.smartfridge.exception.GeminiApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cliente HTTP de la API REST de Gemini. Única clase del proyecto que
 * conoce el formato de payload de Google.
 *
 * <h2>Por qué existe (Fase 11)</h2>
 * Toda esta lógica —construcción del payload, selección de modelo con
 * respaldo, {@code thinkingConfig} según familia de modelo, detección de
 * candidatos vacíos, logging con redacción del Base64— vivía dentro de
 * {@code VisionServiceImpl}. Al añadir el asistente conversacional
 * había dos opciones: duplicarla o extraerla. Duplicarla habría
 * significado mantener dos veces el arreglo del bug de
 * {@code MAX_TOKENS} de la Fase 9, y que el segundo consumidor
 * "heredase" en silencio los errores que ya se habían corregido en el
 * primero.
 *
 * <p>Extraída aquí, {@code VisionServiceImpl} y {@code AsistenteServiceImpl}
 * quedan reducidos a lo suyo: construir el prompt de su caso de uso e
 * interpretar la respuesta. Ninguno de los dos sabe qué es un
 * {@code inlineData} ni un {@code finishReason}.</p>
 *
 * <h2>Qué NO hace</h2>
 * No decide prompts, ni temperatura, ni presupuesto de tokens: eso es
 * política de cada caso de uso y llega por {@link GeminiOpciones}. Este
 * cliente solo sabe hablar con Gemini de forma fiable.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GeminiClient {

    private final RestClient geminiRestClient;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Envía una conversación completa a Gemini y devuelve el texto del
     * primer candidato.
     *
     * @param systemPrompt instrucciones de sistema (personalidad, reglas,
     *                     contexto inyectado). Viaja en
     *                     {@code systemInstruction}, NO como un turno más
     *                     de la conversación: así el modelo lo trata con
     *                     mayor prioridad que cualquier cosa que escriba
     *                     después el usuario, lo que además dificulta que
     *                     una inyección de prompt en el mensaje del
     *                     usuario sobreescriba las reglas.
     * @param conversacion turnos en orden cronológico. Gemini exige que
     *                     empiecen por un turno de usuario y que alternen;
     *                     validarlo es responsabilidad de quien llama.
     * @param opciones     temperatura y presupuesto de tokens
     * @throws GeminiApiException ante cualquier fallo de servicio
     */
    public String generar(String systemPrompt, List<GeminiMensaje> conversacion, GeminiOpciones opciones) {
        if (conversacion == null || conversacion.isEmpty()) {
            throw new IllegalArgumentException("La conversación enviada a Gemini no puede estar vacía");
        }
        try {
            return invocar(properties.model(), systemPrompt, conversacion, opciones);
        } catch (HttpClientErrorException e) {
            if (!esFallbackElegible(e.getStatusCode())) {
                throw new GeminiApiException("Gemini devolvió un error HTTP " + e.getStatusCode()
                        + ": " + e.getResponseBodyAsString(), e);
            }
            log.warn("Modelo Gemini '{}' no disponible ahora mismo ({}, {}); reintentando con el modelo de respaldo '{}'. Cuerpo: {}",
                    properties.model(), e.getStatusCode(), motivo(e.getStatusCode()), properties.fallbackModel(),
                    e.getResponseBodyAsString());
            return invocarRespaldo(systemPrompt, conversacion, opciones);
        } catch (RestClientException e) {
            throw new GeminiApiException("No se pudo contactar con la API de Gemini", e);
        }
    }

    /**
     * 400/404: el modelo principal no existe o no está habilitado en la
     * cuenta/región. 429: cuota agotada — muy fácil de disparar en
     * desarrollo, ya que el nivel gratuito de un modelo recién publicado
     * puede venir limitado a muy pocas peticiones al día (comprueba la
     * cuota real en https://aistudio.google.com/rate-limit). En los tres
     * casos tiene sentido reintentar una vez con el modelo de respaldo;
     * el resto de errores 4xx (401 api-key inválida, 400 por payload
     * mal formado) no se benefician de cambiar de modelo.
     */
    private boolean esFallbackElegible(HttpStatusCode status) {
        return status == HttpStatus.NOT_FOUND
                || status == HttpStatus.BAD_REQUEST
                || status == HttpStatus.TOO_MANY_REQUESTS;
    }

    private String motivo(HttpStatusCode status) {
        return status == HttpStatus.TOO_MANY_REQUESTS ? "cuota agotada" : "modelo no usable";
    }

    /**
     * Aísla la llamada al modelo de respaldo en su propio try/catch: sin
     * esto, si el respaldo TAMBIÉN fallara (p. ej. cuota agotada en los
     * dos modelos), la excepción del segundo intento se escaparía sin
     * convertir a {@link GeminiApiException} y Spring devolvería un 500
     * genérico en vez del 502 controlado.
     */
    private String invocarRespaldo(String systemPrompt, List<GeminiMensaje> conversacion, GeminiOpciones opciones) {
        try {
            return invocar(properties.fallbackModel(), systemPrompt, conversacion, opciones);
        } catch (HttpClientErrorException e) {
            throw new GeminiApiException("El modelo de respaldo '" + properties.fallbackModel()
                    + "' también falló: HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new GeminiApiException("No se pudo contactar con la API de Gemini (modelo de respaldo)", e);
        }
    }

    private String invocar(String modelo, String systemPrompt, List<GeminiMensaje> conversacion,
                           GeminiOpciones opciones) {
        GeminiRequest cuerpo = construirPeticion(modelo, systemPrompt, conversacion, opciones);

        // El Base64 de una imagen se redacta antes de loguear: volcar
        // cientos de KB de una foto real a los logs es ruido y, según el
        // despliegue, un problema de privacidad.
        if (log.isDebugEnabled()) {
            log.debug("Petición a Gemini (modelo={}):\n{}", modelo, toJsonRedactado(cuerpo));
        }

        String cuerpoRespuesta = geminiRestClient.post()
                .uri("/models/{modelo}:generateContent", modelo)
                .header("x-goog-api-key", properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(String.class);

        // JSON crudo ANTES de mapearlo: aquí es donde se ven
        // finishReason / safetyRatings / promptFeedback reales.
        if (log.isDebugEnabled()) {
            log.debug("Respuesta cruda de Gemini (modelo={}):\n{}", modelo, cuerpoRespuesta);
        }

        GeminiResponse respuesta = parsear(cuerpoRespuesta);

        if (respuesta == null || respuesta.candidates() == null || respuesta.candidates().isEmpty()) {
            log.warn("Gemini no devolvió ningún candidato (modelo='{}'). Respuesta cruda: {}", modelo, cuerpoRespuesta);
            throw new GeminiApiException("Gemini no devolvió ningún candidato (modelo '" + modelo + "')");
        }

        Candidate candidato = respuesta.candidates().get(0);
        boolean sinContenido = candidato.content() == null
                || candidato.content().parts() == null
                || candidato.content().parts().isEmpty();

        if (sinContenido) {
            // Se registra el finishReason REAL (MAX_TOKENS, SAFETY,
            // PROHIBITED_CONTENT, RECITATION...) en vez de asumir
            // "seguridad" a ciegas, que fue la pista equivocada que se
            // persiguió durante la Fase 9.
            log.warn("Gemini devolvió un candidato SIN contenido. finishReason='{}', modelo='{}'. Respuesta cruda: {}",
                    candidato.finishReason(), modelo, cuerpoRespuesta);
            throw new GeminiApiException("Gemini devolvió un candidato vacío (finishReason="
                    + candidato.finishReason() + ")");
        }

        // Un candidato puede traer varias "parts"; con modelos thinking,
        // alguna puede venir sin texto. Se concatenan las que sí lo
        // tienen en vez de coger ciegamente la primera.
        StringBuilder texto = new StringBuilder();
        for (PartRespuesta parte : candidato.content().parts()) {
            if (parte.text() != null) {
                texto.append(parte.text());
            }
        }
        return texto.toString();
    }

    private GeminiResponse parsear(String cuerpoRespuesta) {
        try {
            return objectMapper.readValue(cuerpoRespuesta, GeminiResponse.class);
        } catch (Exception e) {
            throw new GeminiApiException("No se pudo interpretar la respuesta de Gemini: " + cuerpoRespuesta, e);
        }
    }

    private GeminiRequest construirPeticion(String modelo, String systemPrompt,
                                            List<GeminiMensaje> conversacion, GeminiOpciones opciones) {
        List<Content> contents = conversacion.stream()
                .map(GeminiClient::aContent)
                .toList();

        return new GeminiRequest(
                new SystemInstruction(List.of(Part.deTexto(systemPrompt))),
                contents,
                new GenerationConfig(opciones.temperature(), opciones.maxOutputTokens(), thinkingConfigPara(modelo))
        );
    }

    private static Content aContent(GeminiMensaje mensaje) {
        if (mensaje.imagen() == null) {
            return new Content(mensaje.rol().valorApi(), List.of(Part.deTexto(mensaje.texto())));
        }
        return new Content(mensaje.rol().valorApi(), List.of(
                Part.deTexto(mensaje.texto()),
                Part.deImagen(new InlineData(mensaje.imagen().mimeType(), mensaje.imagen().base64()))
        ));
    }

    /**
     * gemini-3.x expone {@code thinkingLevel} ("low"/"medium"/"high") y NO
     * admite desactivar el razonamiento del todo — solo bajarlo a "low".
     * gemini-2.5.x expone {@code thinkingBudget} (tokens, 0 = desactivado).
     * Son campos distintos según la familia; mandar el que no corresponde
     * no rompe la petición, pero tampoco surte efecto, así que se elige
     * por el nombre del modelo.
     */
    private ThinkingConfig thinkingConfigPara(String modelo) {
        if (modelo != null && modelo.startsWith("gemini-3")) {
            return ThinkingConfig.nivelBajo();
        }
        return ThinkingConfig.desactivado();
    }

    /** Copia el request sustituyendo el Base64 por su longitud, solo para logging. */
    private String toJsonRedactado(GeminiRequest cuerpo) {
        try {
            GeminiRequest copia = new GeminiRequest(
                    cuerpo.systemInstruction(),
                    cuerpo.contents().stream()
                            .map(c -> new Content(c.role(), c.parts().stream()
                                    .map(p -> p.inlineData() == null
                                            ? p
                                            : Part.deImagen(new InlineData(
                                            p.inlineData().mimeType(),
                                            "<base64 omitido, " + p.inlineData().data().length() + " caracteres>")))
                                    .toList()))
                            .toList(),
                    cuerpo.generationConfig()
            );
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(copia);
        } catch (Exception e) {
            return "<no se pudo serializar la petición para el log: " + e.getMessage() + ">";
        }
    }

    // ------------------------------------------------------------------
    // DTOs internos del payload REST de Gemini. Privados a propósito: son
    // detalle de implementación de "cómo se habla con Gemini", no un
    // contrato del dominio SmartFridge. Quien quiera hablar con el modelo
    // usa GeminiMensaje/GeminiOpciones, que no dependen de Google.
    // ------------------------------------------------------------------

    private record GeminiRequest(SystemInstruction systemInstruction, List<Content> contents,
                                 GenerationConfig generationConfig) {
    }

    private record SystemInstruction(List<Part> parts) {
    }

    /**
     * El campo {@code role} es opcional cuando solo hay un turno, pero
     * OBLIGATORIO en cuanto la conversación tiene varios: sin él Gemini
     * no puede saber qué dijo el usuario y qué dijo el modelo. Se manda
     * siempre para no tener dos caminos distintos según el caso de uso.
     */
    private record Content(String role, List<Part> parts) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Part(String text, InlineData inlineData) {
        static Part deTexto(String text) {
            return new Part(text, null);
        }

        static Part deImagen(InlineData inlineData) {
            return new Part(null, inlineData);
        }
    }

    private record InlineData(String mimeType, String data) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GenerationConfig(double temperature, int maxOutputTokens, ThinkingConfig thinkingConfig) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ThinkingConfig(String thinkingLevel, Integer thinkingBudget) {
        static ThinkingConfig nivelBajo() {
            return new ThinkingConfig("low", null);
        }

        static ThinkingConfig desactivado() {
            return new ThinkingConfig(null, 0);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiResponse(List<Candidate> candidates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Candidate(ContentRespuesta content, String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContentRespuesta(List<PartRespuesta> parts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PartRespuesta(String text) {
    }
}
