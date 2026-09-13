package com.smartfridge.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.smartfridge.dto.AuthResponse;
import com.smartfridge.dto.LoginRequest;
import com.smartfridge.dto.RegisterRequest;
import com.smartfridge.model.Usuario;
import com.smartfridge.service.TokenService;
import com.smartfridge.service.UsuarioService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Endpoints que consume LoginActivity/RegisterActivity en el móvil
 * (ver SmartFridgeApi.java del proyecto Android). Ambos son públicos
 * en SecurityConfig ({@code permitAll()} temporal en /api/**) — es
 * intencional: son precisamente los endpoints que permiten obtener
 * acceso, no pueden exigir estar ya autenticado.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioService usuarioService;
    private final TokenService tokenService;

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registrar(@Valid @RequestBody RegisterRequest request) {
        Usuario usuario = usuarioService.registrar(request.nombre(), request.correo(), request.password());
        return toResponse(usuario);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        Usuario usuario = usuarioService.autenticar(request.correo(), request.password());
        return toResponse(usuario);
    }

    /**
     * Fase 13: el registro también devuelve token. Obligar a iniciar
     * sesión inmediatamente después de crear la cuenta sería pedirle al
     * usuario que demuestre algo que acaba de demostrar.
     */
    private AuthResponse toResponse(Usuario usuario) {
        TokenService.Token token = tokenService.generar(usuario);
        return new AuthResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getCorreo(),
                token.valor(),
                token.tipo(),
                token.expiraEnSegundos());
    }
}
