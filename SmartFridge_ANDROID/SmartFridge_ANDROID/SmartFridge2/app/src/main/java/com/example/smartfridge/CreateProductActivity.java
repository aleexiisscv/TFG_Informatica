package com.example.smartfridge;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CreateProductActivity extends AppCompatActivity {

    private EditText rfidTagInput;
    private EditText nombreInput;
    private EditText plazoCaducidadInput;
    private EditText nutriScoreInput;
    private Button submitButton;

    private Button backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_product);

        // Inicializar los campos
        rfidTagInput = findViewById(R.id.rfidTagInput);
        nombreInput = findViewById(R.id.nombreInput);
        plazoCaducidadInput = findViewById(R.id.plazoCaducidadInput);
        nutriScoreInput = findViewById(R.id.nutriScoreInput);
        submitButton = findViewById(R.id.submitButton);
        backButton = findViewById(R.id.backButton);

        // Botón para registrar el producto
        submitButton.setOnClickListener(v -> {
            String rfidTag = rfidTagInput.getText().toString();
            String nombre = nombreInput.getText().toString();
            String plazoCaducidadStr = plazoCaducidadInput.getText().toString();
            String nutriScore = nutriScoreInput.getText().toString();

            // Validar campos vacíos
            if (rfidTag.isEmpty() || nombre.isEmpty() || plazoCaducidadStr.isEmpty() || nutriScore.isEmpty()) {
                Toast.makeText(this, "Por favor, complete todos los campos", Toast.LENGTH_SHORT).show();
                return;
            }

            // Validar plazo de caducidad
            int plazoCaducidad;
            try {
                plazoCaducidad = Integer.parseInt(plazoCaducidadStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Plazo de caducidad debe ser un número", Toast.LENGTH_SHORT).show();
                return;
            }

            // Validar NutriScore (solo caracteres A-E)
            if (!nutriScore.matches("[A-Ea-e]")) {
                Toast.makeText(this, "NutriScore debe ser una letra entre A y E", Toast.LENGTH_SHORT).show();
                return;
            }
            addProducto(rfidTag, nombre, nutriScore.charAt(0), plazoCaducidad);
            Toast.makeText(this, "Producto registrado con éxito", Toast.LENGTH_SHORT).show();
            finish(); // Finalizar actividad y volver a la anterior

            // Usar el método de insertarProducto de la clase Producto
            /*boolean isInserted = Producto.insertarProductos(rfidTag, nombre, plazoCaducidad, nutriScore.toUpperCase());
            if (isInserted) {
                Toast.makeText(this, "Producto registrado con éxito", Toast.LENGTH_SHORT).show();
                finish(); // Finalizar actividad y volver a la anterior
            } else {
                Toast.makeText(this, "Error al registrar el producto", Toast.LENGTH_SHORT).show();
            }*/
        });

        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(CreateProductActivity.this, DashboardActivity.class);
            startActivity(intent);
            finish();
        });
    }
    public void addProducto(String rfidTag, String nombre, char nutriScore, int plazoCaducidad) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            OkHttpClient client = new OkHttpClient();
            String url = "http://192.168.116.180:8080/ServerExampleUbicomp-1.0-SNAPSHOT/addProducto"; // Cambia <tu-dominio> por la URL donde está desplegado tu servlet

            RequestBody formBody = new FormBody.Builder()
                    .add("rfid_tag", rfidTag)
                    .add("nombre", nombre)
                    .add("nutri_score", String.valueOf(nutriScore))
                    .add("plazo_caducidad", String.valueOf(plazoCaducidad))
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .post(formBody)
                    .build();

            try {
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    System.out.println("Response from server: " + responseData);
                } else {
                    System.err.println("Failed to connect or error from server");
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Error sending the request: " + e.getMessage());
            }
        });
        executor.shutdown();
    }

}
