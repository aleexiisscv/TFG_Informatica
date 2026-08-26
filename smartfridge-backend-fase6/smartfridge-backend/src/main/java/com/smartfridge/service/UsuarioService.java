package com.smartfridge.service;

import com.smartfridge.model.Usuario;

public interface UsuarioService {

    /**
     * @throws com.smartfridge.exception.CorreoYaRegistradoException
     *         si ya existe una cuenta con ese correo
     */
    Usuario registrar(String nombre, String correo, String passwordPlano);

    /**
     * @throws com.smartfridge.exception.CredencialesInvalidasException
     *         si el correo no existe o la contraseña no coincide
     *         (mismo error para ambos casos, ver la excepción)
     */
    Usuario autenticar(String correo, String passwordPlano);
}
