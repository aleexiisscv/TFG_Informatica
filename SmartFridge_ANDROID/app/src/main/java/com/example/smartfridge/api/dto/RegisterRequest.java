package com.example.smartfridge.api.dto;

public class RegisterRequest {
    public String nombre;
    public String correo;
    public String password;

    public RegisterRequest(String nombre, String correo, String password) {
        this.nombre = nombre;
        this.correo = correo;
        this.password = password;
    }
}
