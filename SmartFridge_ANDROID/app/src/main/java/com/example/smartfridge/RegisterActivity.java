package com.example.smartfridge;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartfridge.api.RetrofitClient;
import com.example.smartfridge.api.SesionUsuario;
import com.example.smartfridge.api.dto.AuthResponse;
import com.example.smartfridge.api.dto.RegisterRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Alta de usuario contra {@code POST /api/auth/registro}.
 *
 * <p>QUEDA RESUELTA la incongruencia documentada en la version
 * anterior de esta clase: los {@code R.id} que usaba
 * ({@code nombreInput}, {@code correoInput}, {@code passwordInput},
 * {@code registerButton}) eran "asumidos" porque el layout definitivo
 * no estaba claro. El nuevo {@code activity_register.xml} declara
 * exactamente esos identificadores, asi que codigo y layout ya no
 * pueden divergir.</p>
 *
 * <p>Tambien desaparecen del formulario los {@code CheckBox}
 * "Male"/"Female": ninguna Activity los leia, {@code RegisterRequest}
 * no los transporta y {@code Usuario} del backend no tiene ese campo.
 * Pedir un dato personal que el sistema no procesa es exactamente lo
 * que prohibe el principio de minimizacion de datos.</p>
 */
public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private TextInputLayout nameLayout;
    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText nombreInput;
    private TextInputEditText correoInput;
    private TextInputEditText passwordInput;
    private MaterialButton registerButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        nameLayout = findViewById(R.id.nameLayout);
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        nombreInput = findViewById(R.id.nombreInput);
        correoInput = findViewById(R.id.correoInput);
        passwordInput = findViewById(R.id.passwordInput);
        registerButton = findViewById(R.id.registerButton);

        registerButton.setOnClickListener(v -> intentarRegistro());
        findViewById(R.id.goToLoginButton).setOnClickListener(v -> finish());
    }

    private void intentarRegistro() {
        String nombre = texto(nombreInput);
        String correo = texto(correoInput);
        String password = texto(passwordInput);

        nameLayout.setError(null);
        emailLayout.setError(null);
        passwordLayout.setError(null);

        boolean hayError = false;
        if (nombre.isEmpty()) {
            nameLayout.setError(getString(R.string.auth_error_empty));
            hayError = true;
        }
        if (password.isEmpty()) {
            passwordLayout.setError(getString(R.string.auth_error_empty));
            hayError = true;
        }
        if (correo.isEmpty()) {
            emailLayout.setError(getString(R.string.auth_error_empty));
            hayError = true;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            emailLayout.setError(getString(R.string.auth_error_email));
            hayError = true;
        }
        if (hayError) {
            return;
        }

        registerButton.setEnabled(false);

        RetrofitClient.getApi().registrar(new RegisterRequest(nombre, correo, password))
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthResponse> call,
                                           @NonNull Response<AuthResponse> response) {
                        registerButton.setEnabled(true);
                        if (response.isSuccessful()) {
                            // El backend devuelve token también al
                            // registrarse: obligar a iniciar sesión justo
                            // después sería pedir que se demuestre algo
                            // que se acaba de demostrar. Se guarda por si
                            // en el futuro se entra directo al shell.
                            SesionUsuario sesion = SesionUsuario.get();
                            if (sesion != null && response.body() != null) {
                                sesion.guardar(response.body());
                            }
                            Snackbar.make(registerButton, R.string.auth_ok_registered,
                                    Snackbar.LENGTH_SHORT).show();
                            // Se vuelve al login en lugar de apilar otra
                            // Activity encima: el login sigue vivo debajo.
                            startActivity(new Intent(RegisterActivity.this, LoginActivity.class)
                                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                            finish();
                        } else {
                            // 409 Conflict cuando el correo ya existe.
                            emailLayout.setError(getString(R.string.auth_error_duplicate));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable t) {
                        registerButton.setEnabled(true);
                        Log.e(TAG, "Fallo de red al registrar", t);
                        Snackbar.make(registerButton, R.string.common_network_error,
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private static String texto(TextInputEditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }
}
