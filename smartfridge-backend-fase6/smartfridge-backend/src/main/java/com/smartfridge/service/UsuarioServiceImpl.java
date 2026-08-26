package com.smartfridge.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartfridge.exception.CorreoYaRegistradoException;
import com.smartfridge.exception.CredencialesInvalidasException;
import com.smartfridge.model.Usuario;
import com.smartfridge.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public Usuario registrar(String nombre, String correo, String passwordPlano) {
        usuarioRepository.findByCorreo(correo).ifPresent(existente -> {
            throw new CorreoYaRegistradoException(correo);
        });

        Usuario usuario = Usuario.builder()
                .nombre(nombre)
                .correo(correo)
                // encode() nunca guarda passwordPlano en ningún sitio: lo
                // usa para generar el hash y se descarta. Ver la
                // explicación de BCrypt en el chat.
                .passwordHash(passwordEncoder.encode(passwordPlano))
                .build();

        Usuario guardado = usuarioRepository.save(usuario);
        log.info("Usuario registrado: {} <{}>", guardado.getNombre(), guardado.getCorreo());
        return guardado;
    }

    @Override
    @Transactional(readOnly = true)
    public Usuario autenticar(String correo, String passwordPlano) {
        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(CredencialesInvalidasException::new);

        if (!passwordEncoder.matches(passwordPlano, usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }

        return usuario;
    }
}
