package com.smartfridge.dto;

/**
 * Respuesta del asistente.
 *
 * @param respuesta texto generado por Gemini, en Markdown (la app lo
 *                  renderiza con Markwon)
 * @param contexto  cardinalidades de lo que se inyectó en el prompt.
 *                  Permite distinguir "el modelo se ha inventado un
 *                  ingrediente" de "el frigorífico estaba realmente
 *                  vacío" sin abrir los logs del servidor
 */
public record ChatResponse(String respuesta, ContextoResumen contexto) {
}
