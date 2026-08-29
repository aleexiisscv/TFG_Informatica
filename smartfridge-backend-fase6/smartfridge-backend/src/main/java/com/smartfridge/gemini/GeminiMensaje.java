package com.smartfridge.gemini;

/**
 * Un turno de conversación dirigido a Gemini, expresado en el lenguaje
 * del DOMINIO y no en el del payload REST de Google.
 *
 * <p>Es la pieza que permite que {@code GeminiClient} sirva tanto al
 * caso de uso de visión (Fase 9: un único turno de usuario con texto +
 * imagen) como al del asistente conversacional (Fase 11: N turnos
 * alternos de texto). Sin esta abstracción, el cliente HTTP tendría que
 * exponer los records internos del payload de Gemini y todos sus
 * consumidores quedarían acoplados al formato concreto de la API de
 * Google — justo lo que impediría cambiar de proveedor de LLM sin tocar
 * media aplicación.</p>
 *
 * @param rol    quién habla en este turno
 * @param texto  contenido textual (obligatorio; puede ir acompañado de imagen)
 * @param imagen adjunto opcional, solo para peticiones multimodales
 */
public record GeminiMensaje(Rol rol, String texto, Imagen imagen) {

    /**
     * La API de Gemini solo admite dos roles en {@code contents}:
     * "user" y "model". Se tipan como enum para que sea imposible
     * mandar un rol inválido (p. ej. "assistant", que es el nombre que
     * usa OpenAI y una fuente habitual de errores 400 al portar código
     * entre proveedores).
     */
    public enum Rol {
        USUARIO("user"),
        MODELO("model");

        private final String valorApi;

        Rol(String valorApi) {
            this.valorApi = valorApi;
        }

        public String valorApi() {
            return valorApi;
        }
    }

    /** Imagen en línea, codificada en Base64, tal y como la espera Gemini. */
    public record Imagen(String mimeType, String base64) {
    }

    public static GeminiMensaje usuario(String texto) {
        return new GeminiMensaje(Rol.USUARIO, texto, null);
    }

    public static GeminiMensaje modelo(String texto) {
        return new GeminiMensaje(Rol.MODELO, texto, null);
    }

    public static GeminiMensaje usuarioConImagen(String texto, String mimeType, String base64) {
        return new GeminiMensaje(Rol.USUARIO, texto, new Imagen(mimeType, base64));
    }
}
