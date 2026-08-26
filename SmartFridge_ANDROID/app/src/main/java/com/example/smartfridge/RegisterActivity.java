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
import com.example.smartfridge.api.dto.RegisterRequest;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * PENDIENTE DE BACKEND: llama a POST /api/auth/registro, que TODAVÍA
 * NO EXISTE (ver nota en SmartFridgeApi y LoginActivity).
 *
 * OJO — IDs de vista asumidos: nombreInput / correoInput /
 * passwordInput / registerButton, siguiendo la misma convención de
 * nombres que ya usa CreateProductActivity. Las distintas versiones de
 * activity_register.xml que he podido revisar en el proyecto no
 * dejaban claro el layout definitivo (una incluso cargaba
 * activity_main.xml por error). Ajusta estos R.id a los que tenga tu
 * layout real antes de compilar.
 *
 * También se elimina el campo "inventario" que enviaba el
 * createUser() legacy: Usuario ya no tiene ese campo en el backend
 * (se retiró en la Fase 2 por no tener una relación FK real).
 */
public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        EditText nombreField = findViewById(R.id.nombreInput);
        EditText correoField = findViewById(R.id.correoInput);
        EditText passwordField = findViewById(R.id.passwordInput);
        Button registerButton = findViewById(R.id.registerButton);

        registerButton.setOnClickListener(v -> {
            String nombre = nombreField.getText().toString().trim();
            String correo = correoField.getText().toString().trim();
            String password = passwordField.getText().toString().trim();

            if (nombre.isEmpty() || correo.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Rellena todos los campos", Toast.LENGTH_SHORT).show();
                return;
            }

            registerButton.setEnabled(false);

            RegisterRequest request = new RegisterRequest(nombre, correo, password);
            RetrofitClient.getApi().registrar(request).enqueue(new Callback<AuthResponse>() {
                @Override
                public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                    registerButton.setEnabled(true);
                    if (response.isSuccessful()) {
                        Toast.makeText(RegisterActivity.this, "Cuenta creada. Ya puedes iniciar sesión.", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                        finish();
                    } else {
                        // p. ej. 409 Conflict si el correo ya existe (a
                        // implementar en el futuro UsuarioService)
                        Toast.makeText(RegisterActivity.this, "No se pudo crear la cuenta (¿correo ya registrado?)", Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(Call<AuthResponse> call, Throwable t) {
                    registerButton.setEnabled(true);
                    Log.e(TAG, "Fallo de red al registrar", t);
                    Toast.makeText(RegisterActivity.this, "No se pudo conectar con el servidor", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
