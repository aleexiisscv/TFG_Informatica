package com.smartfridge.dto;

/**
 * Un turno ya cerrado de la conversación, tal y como lo reenvía la app
 * Android en cada petición.
 *
 * <p>El rol se acepta en varias grafías a propósito
 * ("user"/"usuario" y "model"/"assistant"/"asistente"): el backend habla
 * con Gemini, que usa "user"/"model", pero la app es de dominio español
 * y otros clientes podrían venir de una convención estilo OpenAI
 * ("assistant"). Normalizar aquí en vez de exigir una sola grafía evita
 * un 400 por un detalle cosmético del cliente. Un rol no reconocido hace
 * que el turno se descarte, no que la petición falle: perder una línea
 * de historial es preferible a dejar al usuario sin respuesta.</p>
 *
 * @param rol   quién habló ("user" / "usuario" / "model" / "assistant" / "asistente")
 * @param texto contenido del turno
 */
public record ChatTurno(String rol, String texto) {
}
