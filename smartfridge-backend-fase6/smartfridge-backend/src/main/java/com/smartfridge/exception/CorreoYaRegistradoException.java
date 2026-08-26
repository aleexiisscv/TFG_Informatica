package com.smartfridge.exception;

/** Se lanza al intentar registrar una cuenta con un correo que ya existe. */
public class CorreoYaRegistradoException extends RuntimeException {

    public CorreoYaRegistradoException(String correo) {
        super("Ya existe una cuenta registrada con el correo '" + correo + "'");
    }
}
