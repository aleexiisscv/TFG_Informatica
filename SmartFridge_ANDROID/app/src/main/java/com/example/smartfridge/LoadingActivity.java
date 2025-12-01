package com.example.smartfridge;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

public class LoadingActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);

        // Simula la carga durante aproximadamente 3 segundos antes de cambiar de actividad
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // Cambia a la siguiente actividad después de la carga
                Intent intent = new Intent(LoadingActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        }, 3000);  // 3000 milisegundos equivalen a 3 segundos
    }
}
