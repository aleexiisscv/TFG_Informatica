package com.example.smartfridge;

import android.content.ActivityNotFoundException;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartfridge.api.RetrofitClient;
import com.example.smartfridge.api.dto.InventarioDto;
import com.example.smartfridge.ui.util.Imagenes;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Vision Artificial: identificacion automatica de producto por foto.
 *
 * <h3>Flujo</h3>
 * La camara del sistema captura la foto ({@link #onImagenCapturada(Bitmap)}),
 * que se envia de inmediato a {@link #clasificar(Bitmap)}. Este metodo sube
 * la imagen al mismo endpoint que ya usa el nodo de vision del propio
 * frigorifico ({@code POST /api/vision/analizar}, ver
 * {@code VisionController} en el backend). Si Gemini reconoce el producto,
 * el servidor da de alta la unidad en el inventario en el mismo paso
 * (201 Created) y esta pantalla se cierra sola. Si no lo reconoce (422) o
 * el identificador no existe en el catalogo (404), se informa al usuario y
 * el boton "Usar esta imagen" queda disponible como via manual: encadena
 * con {@link CreateProductActivity}, tal y como hacia esta pantalla antes
 * de tener clasificacion automatica.
 *
 * <h3>Sobre permisos</h3>
 * Se usa {@code ActivityResultContracts.TakePicturePreview}, que delega
 * la captura en la aplicacion de camara del sistema. Por eso esta app
 * <b>no declara ni solicita el permiso CAMERA</b>: no accede al sensor
 * directamente. Solo habra que pedirlo el dia que se integre una vista
 * previa en vivo con CameraX, y para entonces estara justificado.
 *
 * <p>La camara del movil es el <i>plan de contingencia</i> de la camara
 * Edge AI del propio frigorifico: permite dar de alta un producto
 * aunque el hardware empotrado no este disponible.</p>
 */
public class ScanProductActivity extends AppCompatActivity {

    private static final String TAG = "ScanProductActivity";

    private ImageView scanPreview;
    private ImageView scanPlaceholder;
    private MaterialButton captureButton;
    private MaterialButton confirmButton;

    @Nullable
    private Bitmap capturaActual;

    @Nullable
    private Call<InventarioDto> llamadaVision;

    private final ActivityResultLauncher<Void> lanzadorCamara =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(),
                    this::onImagenCapturada);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_product);

        scanPreview = findViewById(R.id.scanPreview);
        scanPlaceholder = findViewById(R.id.scanPlaceholder);
        captureButton = findViewById(R.id.captureButton);
        confirmButton = findViewById(R.id.confirmButton);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        captureButton.setOnClickListener(v -> abrirCamara());
        confirmButton.setOnClickListener(v -> confirmar());
    }

    private void abrirCamara() {
        try {
            lanzadorCamara.launch(null);
        } catch (ActivityNotFoundException e) {
            // Emuladores sin camara configurada, o dispositivos sin app
            // de camara. Se informa en vez de dejar el boton "muerto".
            Log.w(TAG, "No hay aplicacion de camara disponible", e);
            Snackbar.make(captureButton, R.string.scan_no_camera, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onImagenCapturada(@Nullable Bitmap bitmap) {
        if (bitmap == null) {
            // El usuario cancelo la captura: no es un error.
            return;
        }
        capturaActual = bitmap;
        scanPreview.setImageBitmap(bitmap);
        scanPreview.setVisibility(View.VISIBLE);
        scanPlaceholder.setVisibility(View.GONE);
        captureButton.setText(R.string.scan_retake);
        confirmButton.setVisibility(View.VISIBLE);

        clasificar(bitmap);
    }

    /**
     * Sube la foto a {@code /api/vision/analizar}. Mientras se resuelve
     * la peticion se deshabilita "Usar esta imagen" para evitar un doble
     * envio; se reactiva solo si la clasificacion automatica no ha
     * podido completar el alta, dejando la via manual disponible.
     */
    private void clasificar(Bitmap captura) {
        byte[] jpeg = Imagenes.comprimirAJpeg(captura);
        if (jpeg == null) {
            return;
        }

        confirmButton.setEnabled(false);
        Snackbar.make(scanPreview, R.string.scan_analizando, Snackbar.LENGTH_INDEFINITE).show();

        RequestBody cuerpo = RequestBody.create(jpeg, MediaType.parse(Imagenes.MIME));
        MultipartBody.Part parte = MultipartBody.Part.createFormData("imagen", "captura.jpg", cuerpo);

        llamadaVision = RetrofitClient.getApi().analizarImagen(parte);
        llamadaVision.enqueue(new Callback<InventarioDto>() {
            @Override
            public void onResponse(@NonNull Call<InventarioDto> call, @NonNull Response<InventarioDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Gemini reconocio el producto y el backend ya lo ha
                    // dado de alta en el inventario: no hace falta pasar
                    // por el formulario manual.
                    String nombre = response.body().nombreProducto;
                    Snackbar.make(scanPreview, getString(R.string.scan_added, nombre), Snackbar.LENGTH_LONG).show();
                    scanPreview.postDelayed(ScanProductActivity.this::finish, 900L);
                } else {
                    // 422 (Gemini no reconocio nada) o 404 (identificador
                    // fuera de catalogo): se cae al alta manual.
                    Log.i(TAG, "Clasificacion automatica sin resultado: HTTP " + response.code());
                    confirmButton.setEnabled(true);
                    Snackbar.make(scanPreview, R.string.scan_no_identificado, Snackbar.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<InventarioDto> call, @NonNull Throwable t) {
                if (call.isCanceled()) {
                    return;
                }
                Log.e(TAG, "Fallo de red al clasificar la imagen", t);
                confirmButton.setEnabled(true);
                Snackbar.make(scanPreview, R.string.common_network_error, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Via manual: se usa cuando la clasificacion automatica no ha
     * identificado el producto (o ha fallado la red). El usuario ya ha
     * visto la foto y solo tiene que escribir el nombre.
     */
    private void confirmar() {
        startActivity(new android.content.Intent(this, CreateProductActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (llamadaVision != null) {
            llamadaVision.cancel();
        }
        // El bitmap de previsualizacion puede ocupar varios MB: se
        // libera la referencia para no retenerlo si la Activity queda
        // en la pila de instancias recientes.
        scanPreview.setImageDrawable(null);
        capturaActual = null;
    }
}
