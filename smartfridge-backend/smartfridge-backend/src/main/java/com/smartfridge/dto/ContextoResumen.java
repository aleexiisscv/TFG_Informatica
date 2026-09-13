package com.smartfridge.dto;

/**
 * Recuento de lo que se inyectó en el prompt para responder a una
 * consulta del asistente.
 *
 * <p>Se devuelve al cliente junto con la respuesta por dos motivos
 * prácticos, ambos relevantes para un TFG:</p>
 * <ol>
 *   <li><b>Depuración de alucinaciones.</b> Si el asistente menciona un
 *       ingrediente inexistente y este resumen dice
 *       {@code unidadesInventario = 0}, el problema está en el prompt,
 *       no en los datos. Sin este dato habría que releer los logs del
 *       servidor para distinguir un caso del otro.</li>
 *   <li><b>Trazabilidad.</b> Permite afirmar en la memoria que la
 *       respuesta se generó sobre N unidades reales de inventario, en
 *       lugar de tener que creerse la palabra del modelo.</li>
 * </ol>
 *
 * <p>No contiene datos personales ni el contenido del prompt: solo
 * cardinalidades.</p>
 */
public record ContextoResumen(
        int unidadesInventario,
        int productosDistintos,
        int catalogoNoDisponible,
        int sensores,
        int alertas
) {
}
