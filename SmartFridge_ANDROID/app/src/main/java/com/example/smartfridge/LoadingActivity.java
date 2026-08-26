package com.example.smartfridge;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Pantalla de arranque.
 *
 * <p>Tres correcciones respecto a la version heredada:</p>
 * <ol>
 *   <li>Extiende {@link AppCompatActivity} y no {@code android.app.Activity}.
 *       Antes NO heredaba el tema Material 3 —de ahi el fondo blanco
 *       fijo del layout original— ni recibia el color dinamico, que se
 *       aplica via {@code ActivityLifecycleCallbacks} de AppCompat.</li>
 *   <li>El {@code Handler} se ancla explicitamente al
 *       {@code Looper.getMainLooper()}. El constructor sin argumentos
 *       esta desaconsejado desde API 30 justamente porque depende del
 *       hilo desde el que se instancie.</li>
 *   <li>La espera baja de 3 000 ms a 1 200 ms y se cancela en
 *       {@code onDestroy}. Tres segundos de espera artificial son tres
 *       segundos que el usuario pierde en cada arranque; y si giraba el
 *       movil durante la espera, el {@code Runnable} original seguia
 *       vivo y lanzaba una segunda Activity.</li>
 * </ol>
 *
 * <p>Va directo a {@link LoginActivity}: la antigua pantalla de
 * bienvenida intermedia ({@code MainActivity} + {@code activity_principal})
 * solo ofrecia dos botones —"Registrarse" e "Iniciar sesion"— que ahora
 * conviven en la propia pantalla de login. Era un paso de mas en el
 * embudo de entrada.</p>
 */
public class LoadingActivity extends AppCompatActivity {

    private static final long DURACION_SPLASH_MS = 1_200L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable irALogin = new Runnable() {
        @Override
        public void run() {
            startActivity(new Intent(LoadingActivity.this, LoginActivity.class));
            finish();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);
        handler.postDelayed(irALogin, DURACION_SPLASH_MS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(irALogin);
    }
}
