package com.example.smartfridge;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import Android.Logic.Usuario;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText usernameField = findViewById(R.id.user);
        EditText passwordField = findViewById(R.id.Password);
        Button loginButton = findViewById(R.id.loginButton);

        loginButton.setOnClickListener(v -> {
            String username = usernameField.getText().toString();
            String password = passwordField.getText().toString();

            if (UserManager.validateUser(username, password)) {
                // Usuario válido, redirige a la siguiente pantalla o actividad principal
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);
                Toast.makeText(LoginActivity.this, "Logged In Correctly", Toast.LENGTH_SHORT).show();
                finish(); // Cierra la actividad de inicio de sesión
            } else {
                // Credenciales incorrectas, muestra un mensaje
                Toast.makeText(LoginActivity.this, "Invalid username or password", Toast.LENGTH_SHORT).show();
                // Limpia los campos
                usernameField.setText("");
                passwordField.setText("");
            }

            // Verificar si el usuario existe en la base de datos
            boolean exists = Usuario.existeUsuario(username, password); // Metodo existente en Logic.Usuario
            if (exists) {
                // Credenciales válidas, redirigir al dashboard
                Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
                startActivity(intent);
                finish();
            } else {
                // Credenciales inválidas
                Toast.makeText(this, "Correo o contraseña incorrectos", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
