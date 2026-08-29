package com.example.smartfridge.api.dto;

/**
 * Un turno ya cerrado de la conversación, tal y como lo espera
 * {@code ChatTurno} del backend.
 *
 * <p>Los valores de {@code rol} son los de la API de Gemini
 * ("user"/"model") y no "usuario"/"asistente": el backend acepta ambas
 * grafías, pero enviar directamente las que usa el modelo evita una
 * traducción intermedia y hace evidente, leyendo el DTO, con qué
 * convención se está hablando.</p>
 */
public class ChatTurnoDto {

    public static final String ROL_USUARIO = "user";
    public static final String ROL_ASISTENTE = "model";

    public String rol;
    public String texto;

    public ChatTurnoDto() {
        // Gson necesita poder instanciar el objeto
    }

    public ChatTurnoDto(String rol, String texto) {
        this.rol = rol;
        this.texto = texto;
    }
}
