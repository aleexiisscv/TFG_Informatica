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

    public ChatRequestDto(String mensaje, List<ChatTurnoDto> historial) {
        this.mensaje = mensaje;
        this.historial = historial;
    }
}
