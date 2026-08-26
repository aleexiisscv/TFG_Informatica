package com.example.smartfridge;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartfridge.api.RetrofitClient;
import com.example.smartfridge.api.dto.AuthResponse;
import com.example.smartfridge.api.dto.LoginRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Inicio de sesion contra {@code POST /api/auth/login}.
 *
 * <p>La logica de red no cambia respecto a la version anterior (ya
 * estaba migrada a Retrofit). Lo que cambia es la <b>capa de
 * presentacion</b>:</p>
 * <ul>
 *   <li>Los errores dejan de mostrarse con {@code Toast} y pasan a
 *       {@link TextInputLayout#setError(CharSequence)} cuando el fallo
 *       es de un campo concreto, y a {@link Snackbar} cuando es global
 *       (red caida). Un Toast se superpone a la interfaz, desaparece
 *       solo y no es accesible para lectores de pantalla; el error del
 *       campo queda anclado al campo que lo provoca.</li>
 *   <li>Se valida el formato del correo en cliente antes de gastar una
 *       peticion de red.</li>
 *   <li>Al entrar se va a {@link MainShellActivity}, no a la antigua
 *       {@code DashboardActivity}, y se limpia la pila para que
 *       "atras" no devuelva al login ya superado.</li>
 * </ul>
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private MaterialButton loginButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);

        loginButton.setOnClickListener(v -> intentarLogin());

        findViewById(R.id.goToRegisterButton).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void intentarLogin() {
        String correo = texto(emailInput);
        String password = texto(passwordInput);

        emailLayout.setError(null);
        passwordLayout.setError(null);

        if (correo.isEmpty() || password.isEmpty()) {
            if (correo.isEmpty()) {
                emailLayout.setError(getString(R.string.auth_error_empty));
            }
            if (password.isEmpty()) {
                passwordLayout.setError(getString(R.string.auth_error_empty));
            }
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            emailLayout.setError(getString(R.string.auth_error_email));
            return;
        }

        loginButton.setEnabled(false); // evita el doble toque con la peticion en vuelo

        RetrofitClient.getApi().login(new LoginRequest(correo, password))
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthResponse> call,
                                           @NonNull Response<AuthResponse> response) {
                        loginButton.setEnabled(true);
                        if (response.isSuccessful() && response.body() != null) {
                            irAlShell();
                        } else {
                            // 401/404 del servidor: credenciales incorrectas.
                            passwordLayout.setError(getString(R.string.auth_error_credentials));
                            if (passwordInput.getText() != null) {
                                passwordInput.getText().clear();
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable t) {
                        loginButton.setEnabled(true);
                        Log.e(TAG, "Fallo de red al iniciar sesion", t);
                        Snackbar.make(loginButton, R.string.common_network_error,
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void irAlShell() {
        Intent intent = new Intent(this, MainShellActivity.class);
        // CLEAR_TASK + NEW_TASK vacia la pila: tras autenticarse, el
        // boton "atras" debe salir de la app, no volver al formulario.
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static String texto(TextInputEditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }
}
