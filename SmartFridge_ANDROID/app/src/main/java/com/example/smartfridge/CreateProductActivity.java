package com.example.smartfridge;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartfridge.api.RetrofitClient;
import com.example.smartfridge.api.dto.ProductoDto;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Refactorizada para consumir POST /api/productos vía Retrofit, en vez
 * de construir a mano un FormBody de OkHttp contra el servlet
 * "addProducto" legacy.
 *
 * CAMBIO DE ALCANCE (Fase 7): el proyecto descarta el RFID como
 * identificador físico. El campo que antes era "RFID Tag" (leído de una
 * pegatina física en el producto) pasa a ser un ID GENÉRICO de texto
 * libre. Sigue viajando en el mismo campo `rfidTag` del contrato REST
 * (ProductoDto / ProductoRequest en el backend) porque esta iteración
 * no toca el backend — pero conceptualmente ya no es un UID de
 * lector RFID: es el identificador que, en la futura integración de
 * Visión Artificial, se corresponderá con la CLASE detectada por el
 * modelo de Computer Vision (p. ej. "leche_brick_1l",
 * "huevos_docena"). Se mantiene el nombre de campo tal cual a
 * propósito, para no romper el contrato con un backend que hoy no se
 * está tocando; renombrarlo a algo como "productId" o "classLabel" en
 * ambas capas a la vez es candidato natural para la fase de IA.
 *
 * NOTA HONESTA (no se actúa sobre esto, solo se documenta): el
 * ProductoController.crear() del backend no comprueba hoy si el ID ya
 * existe antes de guardar. Como Producto usa un ID asignado a mano (no
 * autogenerado), Spring Data JPA puede tratar un ID repetido como una
 * actualización silenciosa (UPDATE) en vez de fallar con un 409
 * Conflict. No es un problema de esta Activity ni se toca aquí — se
 * deja constancia para una futura iteración del backend.
 */
public class CreateProductActivity extends AppCompatActivity {

    private static final String TAG = "CreateProductActivity";

    private EditText idInput;
    private EditText nombreInput;
    private EditText plazoCaducidadInput;
    private EditText nutriScoreInput;
    private Button submitButton;
    private Button backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_product);

        idInput = findViewById(R.id.rfidTagInput);
        nombreInput = findViewById(R.id.nombreInput);
        plazoCaducidadInput = findViewById(R.id.plazoCaducidadInput);
        nutriScoreInput = findViewById(R.id.nutriScoreInput);
        submitButton = findViewById(R.id.submitButton);
        backButton = findViewById(R.id.backButton);

        submitButton.setOnClickListener(v -> onSubmit());

        backButton.setOnClickListener(v -> {
            startActivity(new Intent(CreateProductActivity.this, DashboardActivity.class));
            finish();
        });
    }

    private void onSubmit() {
        String id = idInput.getText().toString().trim();
        String nombre = nombreInput.getText().toString().trim();
        String plazoCaducidadStr = plazoCaducidadInput.getText().toString().trim();
        String nutriScoreStr = nutriScoreInput.getText().toString().trim().toUpperCase();

        if (id.isEmpty() || nombre.isEmpty() || plazoCaducidadStr.isEmpty()) {
            Toast.makeText(this, "Rellena ID, nombre y plazo de caducidad", Toast.LENGTH_SHORT).show();
            return;
        }

        int plazoCaducidad;
        try {
            plazoCaducidad = Integer.parseInt(plazoCaducidadStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "El plazo de caducidad debe ser un número entero de días", Toast.LENGTH_SHORT).show();
            return;
        }

        // El Nutri-Score es opcional (el backend no lo valida como
        // obligatorio); si se escribe algo, debe ser una letra A-E.
        String nutriScore = nutriScoreStr.isEmpty() ? null : nutriScoreStr;
        if (nutriScore != null && !nutriScore.matches("[A-E]")) {
            Toast.makeText(this, "El Nutri-Score debe ser una letra entre A y E", Toast.LENGTH_SHORT).show();
            return;
        }

        submitButton.setEnabled(false); // evita doble-tap mientras la petición está en vuelo

        ProductoDto producto = new ProductoDto(id, nombre, nutriScore, plazoCaducidad);
        RetrofitClient.getApi().crearProducto(producto).enqueue(new Callback<ProductoDto>() {
            @Override
            public void onResponse(Call<ProductoDto> call, Response<ProductoDto> response) {
                submitButton.setEnabled(true);
                if (response.isSuccessful()) {
                    Toast.makeText(CreateProductActivity.this, "Producto creado correctamente", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(CreateProductActivity.this, DashboardActivity.class));
                    finish();
                } else {
                    Log.w(TAG, "Respuesta no exitosa al crear producto: HTTP " + response.code());
                    Toast.makeText(CreateProductActivity.this, "No se pudo crear el producto", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ProductoDto> call, Throwable t) {
                submitButton.setEnabled(true);
                Log.e(TAG, "Fallo de red al crear producto", t);
                Toast.makeText(CreateProductActivity.this, "No se pudo conectar con el servidor", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
