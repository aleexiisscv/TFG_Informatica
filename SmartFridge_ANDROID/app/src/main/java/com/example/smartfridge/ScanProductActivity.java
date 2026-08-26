package com.example.smartfridge;

import android.content.ActivityNotFoundException;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

/**
 * Vision Artificial: interfaz completa, modelo pendiente.
 *
 * <h3>Que resuelve ya</h3>
 * Invocacion de la camara, previsualizacion de la captura, repeticion
 * de la foto y confirmacion. Es decir, todo el flujo de interaccion.
 *
 * <h3>Que falta y donde encaja</h3>
 * El unico hueco es {@link #clasificar(Bitmap)}. Cuando exista el
 * modelo hay dos caminos, ambos compatibles con esta pantalla sin
 * tocar el layout:
 * <ul>
 *   <li><b>En dispositivo</b> (TFLite / ML Kit): se pasa el bitmap al
 *       interprete y se obtiene la clase. Ventaja: funciona sin red y
 *       la imagen no sale del telefono.</li>
 *   <li><b>En servidor</b>: se sube la imagen a un endpoint nuevo del
 *       backend. Ventaja: un modelo mas grande y actualizable sin
 *       publicar una version de la app.</li>
 * </ul>
 * La clase resultante alimentaria el campo {@code rfidTag} del
 * {@code ProductoDto} — el mismo que {@link CreateProductActivity}
 * rellena a mano hoy.
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
     * PUNTO DE INTEGRACION DEL MODELO DE VISION.
     *
     * <p>Hoy solo informa de que la clasificacion automatica aun no
     * esta disponible. Al implementarla, el resultado debe rellenar el
     * identificador del producto que se enviara al backend.</p>
     */
    private void clasificar(Bitmap captura) {
        Snackbar.make(scanPreview, R.string.scan_pending_model, Snackbar.LENGTH_LONG).show();
    }

    /**
     * Mientras no haya clasificador, "confirmar" encadena con el alta
     * manual: el usuario ha visto el producto y solo tiene que escribir
     * su nombre. Es un flujo util desde el primer dia y el mismo punto
     * donde, mas adelante, llegaran los campos ya prerrellenados por el
     * modelo.
     */
    private void confirmar() {
        startActivity(new android.content.Intent(this, CreateProductActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // El bitmap de previsualizacion puede ocupar varios MB: se
        // libera la referencia para no retenerlo si la Activity queda
        // en la pila de instancias recientes.
        scanPreview.setImageDrawable(null);
        capturaActual = null;
    }
}
