package com.example.smartfridge.api.dto;

/**
 * Cardinalidades del contexto que el backend inyectó en el prompt.
 *
 * <p>No se muestra al usuario: sirve para depurar. Si el asistente
 * menciona un ingrediente que no existe y {@code unidadesInventario} es
 * 0, el problema está en el prompt del backend y no en los datos —una
 * distinción que, sin este dato, obligaría a mirar los logs del
 * servidor.</p>
 */
public class ContextoResumenDto {
    public int unidadesInventario;
    public int productosDistintos;
    public int catalogoNoDisponible;
    public int sensores;
    public int alertas;
}
