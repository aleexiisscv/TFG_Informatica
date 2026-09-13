package com.smartfridge.gemini;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Parámetros de generación por caso de uso.
 *
 * <p>Se pasan por llamada y no como configuración global porque los dos
 * usos que tiene el proyecto son opuestos: la clasificación de una
 * imagen quiere temperatura 0 (determinista, una sola respuesta
 * correcta) y el asistente conversacional quiere algo de variedad léxica
 * para no sonar robótico. Fijar un único valor global obligaría a que
 * uno de los dos funcionase peor.</p>
 *
 * @param temperature     0.0 = determinista; valores altos = más creativo
 * @param maxOutputTokens presupuesto TOTAL de salida. Ojo: en los modelos
 *                        "thinking" (gemini-2.5.x y 3.x) los tokens de
 *                        razonamiento interno se descuentan de aquí antes
 *                        de que el modelo escriba una sola palabra de la
 *                        respuesta. Quedarse corto no produce una respuesta
 *                        truncada: produce un candidato VACÍO con
 *                        finishReason=MAX_TOKENS. Es el fallo que se
 *                        depuró en la Fase 9.
 * @param esquema         restricción de formato de salida, o {@code null}
 *                        para texto libre
 */
public record GeminiOpciones(double temperature, int maxOutputTokens, Esquema esquema) {

    /**
     * Restricción de la salida del modelo (Fase 13).
     *
     * <p>Gemini soporta <i>structured output</i>: en lugar de pedirle por
     * favor en el prompt que responda de cierta forma, se declara el
     * formato en la propia petición y el motor de decodificación
     * <b>impide</b> generar cualquier token que no encaje. La diferencia
     * es de naturaleza, no de grado: un prompt es una sugerencia que el
     * modelo puede desatender; un esquema es una restricción que no
     * puede violar.</p>
     *
     * @param mimeType {@code text/x.enum} para un valor de lista cerrada,
     *                 {@code application/json} para un objeto
     * @param tipo     tipo del esquema en la nomenclatura de Gemini
     *                 ({@code STRING}, {@code OBJECT}...)
     * @param valores  valores admitidos cuando el tipo es un enumerado
     */
    public record Esquema(
            String mimeType,
            String tipo,
            // "enum" es palabra reservada en Java, así que el componente
            // se llama "valores" y se renombra al serializar.
            @JsonProperty("enum") List<String> valores) {
    }

    /** Salida de texto libre (asistente conversacional). */
    public static GeminiOpciones de(double temperature, int maxOutputTokens) {
        return new GeminiOpciones(temperature, maxOutputTokens, null);
    }

    /**
     * Salida restringida a uno de los valores dados.
     *
     * <p>El modelo no puede devolver nada fuera de esta lista: no es que
     * se le pida que no lo haga, es que no tiene forma de hacerlo.</p>
     */
    public static GeminiOpciones deEnum(double temperature, int maxOutputTokens, List<String> valores) {
        if (valores == null || valores.isEmpty()) {
            throw new IllegalArgumentException("Un esquema de tipo enum necesita al menos un valor");
        }
        return new GeminiOpciones(temperature, maxOutputTokens,
                new Esquema("text/x.enum", "STRING", List.copyOf(valores)));
    }
}
