package com.smartfridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros del asistente conversacional, bajo el prefijo
 * "smartfridge.asistente" de application.yml.
 *
 * <p>Se separan de {@link GeminiProperties} porque describen cosas
 * distintas: aquellas son "cómo conectar con Google" (clave, modelo,
 * URL) y estas son "cómo debe comportarse nuestro asistente". Mezclarlas
 * obligaría a tocar la configuración de conexión para ajustar algo tan
 * inocuo como la longitud máxima de una respuesta.</p>
 *
 * @param temperature        creatividad del modelo. 0.6 da variedad
 *                           léxica sin inventarse datos; subirlo por
 *                           encima de ~0.9 aumenta las alucinaciones,
 *                           que es justo lo que el contexto RAG intenta
 *                           evitar
 * @param maxOutputTokens    presupuesto de salida. Generoso porque los
 *                           modelos "thinking" descuentan de aquí su
 *                           razonamiento interno ANTES de escribir, y
 *                           una receta paso a paso es larga
 * @param maxTurnosHistorial cuántos turnos anteriores se reenvían a
 *                           Gemini. Acota el tamaño del prompt y, por
 *                           tanto, el coste y la latencia
 * @param maxCaracteresTurno recorte por turno del historial, para que un
 *                           mensaje muy largo no se coma el presupuesto
 */
@ConfigurationProperties(prefix = "smartfridge.asistente")
public record AsistenteProperties(
        Double temperature,
        Integer maxOutputTokens,
        Integer maxTurnosHistorial,
        Integer maxCaracteresTurno
) {

    /**
     * Valores por defecto aplicados en el propio constructor compacto:
     * así la aplicación arranca aunque application.yml no declare la
     * sección "smartfridge.asistente" (por ejemplo, en un despliegue que
     * arrastre un yml antiguo), en vez de fallar con un NullPointerException
     * en la primera consulta.
     */
    public AsistenteProperties {
        temperature = temperature != null ? temperature : 0.6;
        maxOutputTokens = maxOutputTokens != null ? maxOutputTokens : 2048;
        maxTurnosHistorial = maxTurnosHistorial != null ? maxTurnosHistorial : 10;
        maxCaracteresTurno = maxCaracteresTurno != null ? maxCaracteresTurno : 1500;
    }
}
