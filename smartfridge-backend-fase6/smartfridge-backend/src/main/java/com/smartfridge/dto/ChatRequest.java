package com.smartfridge.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Petición al asistente.
 *
 * <h2>Por qué el historial viaja del cliente al servidor</h2>
 * El backend es <b>sin estado</b> por diseño: no guarda conversaciones
 * ni en memoria ni en base de datos. La app envía en cada turno los
 * mensajes anteriores y el servidor los reenvía a Gemini. Tres motivos:
 * <ol>
 *   <li>No introduce estado de sesión en una API REST que hoy no lo
 *       tiene, y que por tanto puede escalar horizontalmente o
 *       reiniciarse sin perder conversaciones a medias.</li>
 *   <li>Evita tener que decidir cuándo caduca y quién limpia una
 *       conversación huérfana, que es donde suelen aparecer las fugas de
 *       memoria en este tipo de servicios.</li>
 *   <li>El contexto del frigorífico se recalcula igualmente en cada
 *       turno (ver {@code ContextoFrigorificoService}), así que guardar
 *       la conversación en servidor no ahorraría el trabajo caro.</li>
 * </ol>
 * El coste es más tráfico por petición, asumible para conversaciones
 * cortas y acotado por {@code max-turnos-historial}.
 *
 * @param mensaje   consulta del usuario en este turno
 * @param historial turnos anteriores en orden cronológico; puede ser
 *                  {@code null} o vacío en el primer mensaje. Debe
 *                  contener solo turnos ya completados: el backend
 *                  descarta defensivamente los turnos de usuario sin
 *                  respuesta al final de la lista.
 */
public record ChatRequest(

        @NotBlank(message = "El mensaje no puede estar vacío")
        @Size(max = 2000, message = "El mensaje no puede superar los 2000 caracteres")
        String mensaje,

        List<ChatTurno> historial
) {
}
