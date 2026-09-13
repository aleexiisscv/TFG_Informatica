package com.smartfridge.service;

import com.smartfridge.dto.ContextoResumen;

/**
 * Fotografía del estado del frigorífico en el instante de una consulta,
 * ya serializada al bloque de texto que se inyecta en el prompt.
 *
 * @param texto   bloque delimitado listo para incrustar en el system prompt
 * @param resumen cardinalidades de lo incluido, para trazabilidad
 */
public record ContextoFrigorifico(String texto, ContextoResumen resumen) {
}
