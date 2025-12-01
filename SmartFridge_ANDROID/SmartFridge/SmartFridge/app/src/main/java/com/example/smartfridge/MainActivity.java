package com.example.smartfridge;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_principal);

        Button registerButton = findViewById(R.id.registerButton);
        registerButton.setOnClickListener(v -> {
            // Cambia a la actividad de inicio de sesión
            Intent intent1 = new Intent(MainActivity.this, RegisterActivity.class);
            startActivity(intent1);
            finish();
        });

        Button loginButton = findViewById(R.id.loginButton);
        loginButton.setOnClickListener(v -> {
            // Cambia a la actividad de inicio de sesión
            Intent intent2 = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent2);
            finish();
        });
    }
}
