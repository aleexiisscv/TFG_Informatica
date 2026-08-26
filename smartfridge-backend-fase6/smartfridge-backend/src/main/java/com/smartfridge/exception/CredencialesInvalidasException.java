package com.smartfridge.exception;

/**
 * Se lanza tanto si el correo no existe como si la contraseña no
 * coincide. Se usa deliberadamente la MISMA excepción (y el mismo
 * mensaje genérico) para ambos casos: si se distinguiera "correo no
 * encontrado" de "contraseña incorrecta" con mensajes distintos, un
 * atacante podría usar el endpoint de login para averiguar qué
 * correos están registrados en el sistema (ataque de enumeración de
 * usuarios) probando direcciones una a una.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Correo o contraseña incorrectos");
    }
}
