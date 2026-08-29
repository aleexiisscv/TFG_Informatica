package com.smartfridge.gemini;

/**
 * Parámetros de generación por caso de uso.
 *
 * <p>Se pasan por llamada y no como configuración global porque los dos
 * usos que tiene el proyecto son opuestos: la clasificación de una
 * imagen quiere temperatura 0 (determinista, una sola palabra correcta)
 * y el asistente conversacional quiere algo de variedad léxica para no
 * sonar robótico. Fijar un único valor global obligaría a que uno de
 * los dos casos funcionase peor.</p>
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
 */
public record GeminiOpciones(double temperature, int maxOutputTokens) {

    public static GeminiOpciones de(double temperature, int maxOutputTokens) {
        return new GeminiOpciones(temperature, maxOutputTokens);
    }
}
