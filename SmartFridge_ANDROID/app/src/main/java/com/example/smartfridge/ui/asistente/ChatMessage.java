package com.example.smartfridge.ui.asistente;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Un elemento de la conversación.
 *
 * <p>Ampliado en la Fase 11 con un {@link Tipo}. En la fase anterior
 * bastaba un booleano "delUsuario" porque solo había dos clases de
 * burbuja; ahora la lista contiene además el indicador de "escribiendo"
 * y los avisos de error, que son elementos de la conversación aunque no
 * sean mensajes. Modelarlos como filas más de la lista —en vez de como
 * vistas sueltas encima del RecyclerView— hace que el scroll, las
 * animaciones y el anclaje al final funcionen igual para todos sin
 * código adicional.</p>
 *
 * <p>Sigue siendo <b>inmutable</b>: los campos son {@code final} y no
 * hay setters. Una lista cuyos elementos mutan por debajo rompería el
 * cálculo de diferencias de {@code DiffUtil}.</p>
 */
public final class ChatMessage {

    private static final AtomicLong SECUENCIA = new AtomicLong();

    /** Identificador estable del indicador de escritura: nunca hay más de uno. */
    private static final long ID_ESCRIBIENDO = -1L;

    public enum Tipo {
        /** Mensaje escrito por la persona. */
        USUARIO,
        /** Respuesta generada por el asistente (Markdown). */
        ASISTENTE,
        /** Placeholder mientras se espera la respuesta del modelo. */
        ESCRIBIENDO,
        /** Fallo de red o del servidor, con opción de reintentar. */
        ERROR
    }

    public final long id;
    public final String texto;
    public final Tipo tipo;

    /**
     * Fase 12: el turno del usuario llevaba una fotografía adjunta.
     *
     * <p>No se guarda el bitmap: la miniatura ya se descartó tras
     * enviarla y retenerla por cada mensaje haría crecer la memoria de la
     * conversación sin límite. Basta con saber que la hubo, para poder
     * indicarlo en la burbuja y —sobre todo— para que el lector de
     * pantalla lo mencione: sin esa marca, quien no ve la pantalla no
     * tendría forma de saber que envió una foto.</p>
     */
    public final boolean llevaImagen;

    private ChatMessage(long id, String texto, Tipo tipo, boolean llevaImagen) {
        this.id = id;
        this.texto = texto;
        this.tipo = tipo;
        this.llevaImagen = llevaImagen;
    }

    public static ChatMessage delUsuario(String texto) {
        return delUsuario(texto, false);
    }

    public static ChatMessage delUsuario(String texto, boolean llevaImagen) {
        return new ChatMessage(SECUENCIA.incrementAndGet(), texto, Tipo.USUARIO, llevaImagen);
    }

    public static ChatMessage delAsistente(String texto) {
        return new ChatMessage(SECUENCIA.incrementAndGet(), texto, Tipo.ASISTENTE, false);
    }

    /**
     * Id fijo a propósito: al añadirlo y quitarlo siempre con el mismo
     * identificador, DiffUtil lo trata como la MISMA fila apareciendo y
     * desapareciendo, y la animación es limpia. Con un id nuevo cada vez,
     * la lista insertaría y borraría filas distintas y parpadearía.
     */
    public static ChatMessage escribiendo() {
        return new ChatMessage(ID_ESCRIBIENDO, "", Tipo.ESCRIBIENDO, false);
    }

    public static ChatMessage error(String texto) {
        return new ChatMessage(SECUENCIA.incrementAndGet(), texto, Tipo.ERROR, false);
    }

    /**
     * Solo los mensajes reales viajan al backend como historial: el
     * indicador de escritura y los avisos de error son estado de la
     * interfaz, no turnos de conversación. Enviarlos confundiría al
     * modelo con turnos vacíos o con textos de error que él nunca dijo.
     */
    public boolean esTurnoDeConversacion() {
        return tipo == Tipo.USUARIO || tipo == Tipo.ASISTENTE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatMessage)) {
            return false;
        }
        ChatMessage otro = (ChatMessage) o;
        return id == otro.id && tipo == otro.tipo && llevaImagen == otro.llevaImagen
                && Objects.equals(texto, otro.texto);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, texto, tipo, llevaImagen);
    }
}
