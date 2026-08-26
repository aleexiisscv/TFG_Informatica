package com.example.smartfridge;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartfridge.api.RetrofitClient;
import com.example.smartfridge.api.dto.AuthResponse;
import com.example.smartfridge.api.dto.LoginRequest;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * PENDIENTE DE BACKEND: llama a POST /api/auth/login, que TODAVÍA NO
 * EXISTE en el backend Spring Boot (solo están construidos
 * /api/productos, /api/inventario, /api/sensores y /api/registros).
 * El código de esta clase ya está listo tal y como debe quedar; solo
 * falta que el AuthController exista en el servidor.
 *
 * Cambio de arquitectura importante respecto al sistema legacy: antes
 * esta Activity descargaba la tabla "usuarios" COMPLETA (con
 * contraseñas) y comparaba las credenciales en el propio cliente.
 * Ahora el móvil solo envía correo+password al servidor y es EL
 * SERVIDOR quien valida contra el hash BCrypt; el teléfono nunca ve ni
 * maneja la contraseña de otro usuario.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText correoField = findViewById(R.id.user);
        EditText passwordField = findViewById(R.id.Password);
        Button loginButton = findViewById(R.id.loginButton);

        loginButton.setOnClickListener(v -> {
            String correo = correoField.getText().toString().trim();
            String password = passwordField.getText().toString().trim();

            if (correo.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Introduce correo y contraseña", Toast.LENGTH_SHORT).show();
                return;
            }

            loginButton.setEnabled(false); // evita doble-tap mientras la petición está en vuelo

            LoginRequest request = new LoginRequest(correo, password);
            RetrofitClient.getApi().login(request).enqueue(new Callback<AuthResponse>() {
                @Override
                public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                    loginButton.setEnabled(true);
                    if (response.isSuccessful() && response.body() != null) {
                        Toast.makeText(LoginActivity.this, "Sesión iniciada", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
                        finish();
                    } else {
                        // 401/404 del servidor: credenciales incorrectas
                        Toast.makeText(LoginActivity.this, "Correo o contraseña incorrectos", Toast.LENGTH_SHORT).show();
                        passwordField.setText("");
                    }
                }

                @Override
                public void onFailure(Call<AuthResponse> call, Throwable t) {
                    loginButton.setEnabled(true);
                    Log.e(TAG, "Fallo de red al iniciar sesión", t);
                    Toast.makeText(LoginActivity.this, "No se pudo conectar con el servidor", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
