package com.smartfridge.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartfridge.model.Usuario;

/** Acceso a la tabla "usuarios". */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     * Usado tanto en el login (buscar por correo antes de comparar el
     * hash) como en el registro (comprobar que el correo no esté ya en
     * uso antes de crear la cuenta).
     */
    Optional<Usuario> findByCorreo(String correo);
}
