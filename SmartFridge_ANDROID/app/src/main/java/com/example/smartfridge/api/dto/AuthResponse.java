package com.example.smartfridge.api.dto;

/**
 * Forma PROVISIONAL de la respuesta de /api/auth/login y
 * /api/auth/registro — ese endpoint todavía no existe en el backend.
 * Cuando se implemente autenticación real (JWT, ver TODO en
 * SecurityConfig del backend), lo natural es añadir aquí un campo
 * `token` y que RetrofitClient lo adjunte como cabecera Authorization
 * en las siguientes peticiones mediante un Interceptor de OkHttp.
 */
public class AuthResponse {
    public Long id;
    public String nombre;
    public String correo;
}
