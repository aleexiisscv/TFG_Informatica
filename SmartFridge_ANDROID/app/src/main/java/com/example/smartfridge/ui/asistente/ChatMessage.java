package com.example.smartfridge.ui.asistente;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Un mensaje de la conversacion.
 *
 * <p>Modelo deliberadamente minimo y <b>inmutable</b>: los campos son
 * {@code final} y no hay setters. Un mensaje ya enviado no cambia, y
 * hacerlo inmutable garantiza que {@code DiffUtil} pueda comparar
 * instancias sin sorpresas (una lista cuyos elementos mutan por debajo
 * rompe el calculo de diferencias).</p>
 *
 * <p>El {@code id} autoincremental existe solo para que DiffUtil pueda
 * distinguir dos mensajes con el mismo texto —perfectamente posible si
 * el usuario repite una pregunta— sin confundirlos.</p>
 */
public final class ChatMessage {

    private static final AtomicLong SECUENCIA = new AtomicLong();

    public final long id;
    public final String texto;
    public final boolean delUsuario;

    private ChatMessage(String texto, boolean delUsuario) {
        this.id = SECUENCIA.incrementAndGet();
        this.texto = texto;
        this.delUsuario = delUsuario;
    }

    public static ChatMessage delUsuario(String texto) {
        return new ChatMessage(texto, true);
    }

    public static ChatMessage delAsistente(String texto) {
        return new ChatMessage(texto, false);
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
        return id == otro.id && delUsuario == otro.delUsuario && Objects.equals(texto, otro.texto);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, texto, delUsuario);
    }
}
