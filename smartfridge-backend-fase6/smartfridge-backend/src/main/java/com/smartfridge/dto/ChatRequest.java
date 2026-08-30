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
 *
 * @param mensaje        consulta del usuario en este turno
 * @param historial      turnos anteriores en orden cronológico; puede ser
 *                       {@code null} o vacío en el primer mensaje. Debe
 *                       contener solo turnos ya completados: el backend
 *                       descarta defensivamente los turnos de usuario sin
 *                       respuesta al final de la lista.
 * @param imagenBase64   <b>Fase 12.</b> Imagen opcional adjunta a ESTE
 *                       turno, codificada en Base64. Se tolera el prefijo
 *                       de <i>data URL</i>. El tope de 4 000 000 de
 *                       caracteres (~3 MB de binario) es una red de
 *                       seguridad, no el tamaño esperado: la app
 *                       reescala a 1024 px y comprime a JPEG antes de
 *                       enviar, con lo que una foto real ronda los
 *                       200 KB. Sin este límite, un cliente mal
 *                       implementado podría mandar una foto de 12 MP en
 *                       crudo y agotar la memoria del servidor al
 *                       deserializar el JSON.
 * @param imagenMimeType MIME de la imagen. Opcional: si no llega, el
 *                       backend lo deduce de los primeros bytes y, en
 *                       último término, asume JPEG.
 */
public record ChatRequest(

        @NotBlank(message = "El mensaje no puede estar vacío")
        @Size(max = 2000, message = "El mensaje no puede superar los 2000 caracteres")
        String mensaje,

        List<ChatTurno> historial,

        @Size(max = 4_000_000, message = "La imagen adjunta es demasiado grande")
        String imagenBase64,

        String imagenMimeType
) {

    /**
     * Compatibilidad con los clientes anteriores a la Fase 12, que
     * mandan un JSON de dos campos. Jackson usa el constructor canónico
     * y deja los campos ausentes a {@code null}, así que este atajo es
     * para el código Java (y los tests), no para la deserialización.
     */
    public ChatRequest(String mensaje, List<ChatTurno> historial) {
        this(mensaje, historial, null, null);
    }

    /** {@code true} si este turno lleva imagen utilizable. */
    public boolean tieneImagen() {
        return imagenBase64 != null && !imagenBase64.isBlank();
    }
}
