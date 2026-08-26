package com.example.smartfridge.api.dto;

public class LoginRequest {
    public String correo;
    public String password;

    public LoginRequest(String correo, String password) {
        this.correo = correo;
        this.password = password;
    }
}
