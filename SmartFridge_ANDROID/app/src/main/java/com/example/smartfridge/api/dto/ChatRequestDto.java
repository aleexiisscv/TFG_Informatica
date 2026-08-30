package com.example.smartfridge.api.dto;

import java.util.List;

/**
 * Petición a {@code POST /api/asistente/chat}.
 *
 * <p>El historial viaja en cada petición porque el backend es sin
 * estado a propósito: no guarda conversaciones. La app es la dueña del
 * hilo de conversación —que ya tiene en memoria para pintarlo— y el
 * servidor se limita a reenviarlo a Gemini junto con el contexto fresco
 * del frigorífico.</p>
 *
 * <p>Solo se envían turnos COMPLETADOS: el mensaje que se está enviando
 * ahora va en {@code mensaje}, no en {@code historial}. El backend
 * descarta defensivamente los turnos de usuario sin respuesta al final
 * de la lista, pero la app no debe apoyarse en eso.</p>
 */
public class ChatRequestDto {

    public String mensaje;
    public List<ChatTurnoDto> historial;

    /**
     * Fase 12: fotografía opcional adjunta a ESTE turno, en Base64 sin
     * saltos de línea. La imagen ya viene reescalada a 1024 px y
     * comprimida a JPEG por {@code Imagenes}; enviarla en crudo
     * multiplicaría por treinta el tamaño de la petición sin que el
     * modelo de visión reconociera mejor el producto.
     *
     * <p>Gson omite los campos {@code null} por defecto, así que cuando
     * no hay foto el JSON sale idéntico al de la fase anterior: los
     * clientes antiguos y el backend nuevo siguen entendiéndose.</p>
     */
    public String imagenBase64;

    /** MIME de la imagen. Siempre {@code image/jpeg} tras la compresión. */
    public String imagenMimeType;

    public ChatRequestDto(String mensaje, List<ChatTurnoDto> historial) {
        this(mensaje, historial, null, null);
    }

    public ChatRequestDto(String mensaje, List<ChatTurnoDto> historial,
                          String imagenBase64, String imagenMimeType) {
        this.mensaje = mensaje;
        this.historial = historial;
        this.imagenBase64 = imagenBase64;
        this.imagenMimeType = imagenMimeType;
    }
}
