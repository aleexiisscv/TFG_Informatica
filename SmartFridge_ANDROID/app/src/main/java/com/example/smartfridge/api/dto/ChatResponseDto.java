package com.example.smartfridge.api.dto;

/**
 * Respuesta de {@code POST /api/asistente/chat}.
 *
 * <p>{@code respuesta} llega en Markdown: el system prompt del backend
 * pide explícitamente negritas, listas y pasos numerados porque el
 * contenido típico son recetas. La app lo renderiza con Markwon en vez
 * de mostrar los asteriscos en crudo.</p>
 */
public class ChatResponseDto {
    public String respuesta;
    public ContextoResumenDto contexto;
}
