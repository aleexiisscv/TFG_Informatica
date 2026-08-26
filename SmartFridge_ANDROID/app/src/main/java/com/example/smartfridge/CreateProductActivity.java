package com.example.smartfridge;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartfridge.api.RetrofitClient;
import com.example.smartfridge.api.dto.ProductoDto;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Alta manual de producto contra {@code POST /api/productos}.
 *
 * <p>La logica de red se conserva de la version anterior. Lo que cambia
 * es la presentacion y el ENCUADRE del primer campo:</p>
 *
 * <p>El proyecto ha descartado el RFID como identificador fisico, asi
 * que el campo deja de llamarse "Etiqueta RFID" y pasa a ser
 * "Identificador del producto", con texto de ayuda que anticipa su uso
 * real: sera la <b>clase</b> que devuelva el modelo de vision
 * (p. ej. {@code leche_brick_1l}). Se mantienen a proposito el
 * {@code R.id.rfidTagInput} y el campo {@code rfidTag} del DTO: esta
 * iteracion no toca el backend y renombrar solo un lado del contrato
 * romperia la serializacion. El renombrado coordinado (app + backend +
 * columna) queda anotado como trabajo futuro.</p>
 *
 * <p>Los errores pasan de {@code Toast} a {@link TextInputLayout#setError},
 * de modo que el mensaje queda anclado al campo que lo provoca en lugar
 * de flotar sobre la pantalla y desvanecerse.</p>
 *
 * <p>NOTA HEREDADA (se documenta, no se actua): {@code ProductoController.crear()}
 * no comprueba si el identificador ya existe. Como {@code Producto} usa
 * un ID asignado a mano, Spring Data JPA puede tratar un ID repetido
 * como un UPDATE silencioso en vez de responder 409 Conflict. Es un
 * asunto del backend, no de esta pantalla.</p>
 */
public class CreateProductActivity extends AppCompatActivity {

    private static final String TAG = "CreateProductActivity";

    private TextInputLayout rfidTagLayout, nombreLayout, plazoCaducidadLayout, nutriScoreLayout;
    private TextInputEditText rfidTagInput, nombreInput, plazoCaducidadInput, nutriScoreInput;
    private MaterialButton submitButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_product);

        rfidTagLayout = findViewById(R.id.rfidTagLayout);
        nombreLayout = findViewById(R.id.nombreLayout);
        plazoCaducidadLayout = findViewById(R.id.plazoCaducidadLayout);
        nutriScoreLayout = findViewById(R.id.nutriScoreLayout);

        rfidTagInput = findViewById(R.id.rfidTagInput);
        nombreInput = findViewById(R.id.nombreInput);
        plazoCaducidadInput = findViewById(R.id.plazoCaducidadInput);
        nutriScoreInput = findViewById(R.id.nutriScoreInput);

        submitButton = findViewById(R.id.submitButton);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        // El boton "Volver" del layout heredado desaparece: la flecha de
        // la toolbar es el patron estandar de Android para retroceder, y
        // ademas convive con el gesto/boton del sistema.
        toolbar.setNavigationOnClickListener(v -> finish());

        submitButton.setOnClickListener(v -> enviar());
    }

    private void enviar() {
        String id = texto(rfidTagInput);
        String nombre = texto(nombreInput);
        String plazoTexto = texto(plazoCaducidadInput);
        String nutriTexto = texto(nutriScoreInput).toUpperCase();

        limpiarErrores();

        boolean hayError = false;
        if (id.isEmpty()) {
            rfidTagLayout.setError(getString(R.string.product_error_required));
            hayError = true;
        }
        if (nombre.isEmpty()) {
            nombreLayout.setError(getString(R.string.product_error_required));
            hayError = true;
        }

        int plazoCaducidad = 0;
        if (plazoTexto.isEmpty()) {
            plazoCaducidadLayout.setError(getString(R.string.product_error_required));
            hayError = true;
        } else {
            try {
                plazoCaducidad = Integer.parseInt(plazoTexto);
            } catch (NumberFormatException e) {
                plazoCaducidadLayout.setError(getString(R.string.product_error_days));
                hayError = true;
            }
        }

        // El Nutri-Score es opcional: el backend no lo exige. Pero si se
        // escribe algo, tiene que ser una letra A-E.
        String nutriScore = nutriTexto.isEmpty() ? null : nutriTexto;
        if (nutriScore != null && !nutriScore.matches("[A-E]")) {
            nutriScoreLayout.setError(getString(R.string.product_error_nutriscore));
            hayError = true;
        }

        if (hayError) {
            return;
        }

        submitButton.setEnabled(false);

        ProductoDto producto = new ProductoDto(id, nombre, nutriScore, plazoCaducidad);
        RetrofitClient.getApi().crearProducto(producto).enqueue(new Callback<ProductoDto>() {
            @Override
            public void onResponse(@NonNull Call<ProductoDto> call,
                                   @NonNull Response<ProductoDto> response) {
                submitButton.setEnabled(true);
                if (response.isSuccessful()) {
                    // Se cierra sin lanzar otra Activity: el shell sigue
                    // vivo debajo y su onResume recarga el inventario.
                    Snackbar.make(submitButton, R.string.product_ok, Snackbar.LENGTH_SHORT).show();
                    submitButton.postDelayed(CreateProductActivity.this::finish, 600L);
                } else {
                    Log.w(TAG, "Respuesta no exitosa al crear producto: HTTP " + response.code());
                    Snackbar.make(submitButton, R.string.product_ko, Snackbar.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ProductoDto> call, @NonNull Throwable t) {
                submitButton.setEnabled(true);
                Log.e(TAG, "Fallo de red al crear producto", t);
                Snackbar.make(submitButton, R.string.common_network_error, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void limpiarErrores() {
        rfidTagLayout.setError(null);
        nombreLayout.setError(null);
        plazoCaducidadLayout.setError(null);
        nutriScoreLayout.setError(null);
    }

    private static String texto(TextInputEditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }
}
