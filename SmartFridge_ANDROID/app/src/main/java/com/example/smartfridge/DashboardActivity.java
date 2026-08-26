package com.example.smartfridge;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import com.example.smartfridge.api.RetrofitClient;
import com.example.smartfridge.api.dto.InventarioDto;
import com.example.smartfridge.api.dto.RegistroDto;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Refactorizada para consumir el backend Spring Boot vía Retrofit, en
 * vez de ServerConnectionThread + HttpURLConnection contra los
 * Servlets legacy.
 *
 * Simplificación notable: antes había que descargar las tablas
 * "productos" e "inventario" por separado y cruzarlas a mano en el
 * cliente (ver setCombinedProductList en la versión anterior, con un
 * HashMap<String, JSONObject> para el join). Ahora GET /api/inventario
 * ya devuelve nombreProducto y nutriScore aplanados en cada fila —el
 * backend hace el JOIN, no el móvil— así que aquí solo queda pintar.
 */
public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";

    private TableLayout tableProductos;
    private Switch switchRFID;
    private Button logoutButton;
    private Button statsButton;
    private Button createProductButton;
    private Button sensorButton;
    private ImageButton alertBellButton;
    private View alertIndicator;

    private final Handler handler = new Handler();
    private Runnable alertCheckRunnable;
    private String alertaDescripcion = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        tableProductos = findViewById(R.id.tableProductos);
        switchRFID = findViewById(R.id.switchRFID);
        logoutButton = findViewById(R.id.logoutButton);
        statsButton = findViewById(R.id.statsButton);
        createProductButton = findViewById(R.id.createProductButton);
        sensorButton = findViewById(R.id.sensorButton);
        alertBellButton = findViewById(R.id.alertBellButton);
        alertIndicator = findViewById(R.id.alertIndicator);

        switchRFID.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // PENDIENTE DE DECISIÓN DE ARQUITECTURA: este switch pretendía
            // cambiar el modo INSERTAR/ELIMINAR del frigorífico. Hoy ese
            // modo solo se puede cambiar publicando en el topic MQTT
            // "frigorifico/modo" (ver EstadoModoFrigorifico en el
            // backend); no existe todavía un endpoint REST equivalente.
            // Dos caminos posibles para una futura iteración:
            //   (a) el propio móvil publica por MQTT directamente
            //       (ya hay dependencias de Paho en build.gradle.kts), o
            //   (b) se añade un POST /api/modo en el backend que delegue
            //       en EstadoModoFrigorifico.
            // Se deja sin conectar a propósito hasta decidir cuál.
            String modo = isChecked ? "INSERTAR" : "ELIMINAR";
            Toast.makeText(this, "Modo " + modo + " (aún no conectado al servidor)", Toast.LENGTH_SHORT).show();
        });

        logoutButton.setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, LoginActivity.class));
            finish();
        });

        statsButton.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, StatsActivity.class)));

        createProductButton.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, CreateProductActivity.class)));

        sensorButton.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, SensorActivity.class)));

        alertBellButton.setOnClickListener(v -> {
            String mensaje = alertaDescripcion.isEmpty() ? "Sin alertas activas" : alertaDescripcion;
            Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
        });

        cargarInventario();
        iniciarComprobacionDeAlertas();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Por si se vuelve aquí tras crear un producto o tras un evento
        // RFID reciente en CreateProductActivity/SensorActivity.
        cargarInventario();
    }

    private void cargarInventario() {
        RetrofitClient.getApi().listarInventario().enqueue(new Callback<List<InventarioDto>>() {
            @Override
            public void onResponse(Call<List<InventarioDto>> call, Response<List<InventarioDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    pintarTabla(response.body());
                } else {
                    Log.w(TAG, "Respuesta no exitosa al listar inventario: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<InventarioDto>> call, Throwable t) {
                // Fallo de red (backend caído, IP mal configurada en
                // RetrofitClient, sin conexión...). A diferencia del
                // sistema legacy, aquí el fallo no tumba la Activity: se
                // registra y se informa al usuario, la tabla simplemente
                // no se actualiza en este ciclo.
                Log.e(TAG, "Fallo de red al cargar el inventario", t);
                Toast.makeText(DashboardActivity.this, "No se pudo conectar con el servidor", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void pintarTabla(List<InventarioDto> inventario) {
        // El callback de Retrofit/OkHttp NO llega en el hilo principal:
        // cualquier manipulación de vistas debe volver explícitamente al
        // hilo de UI con runOnUiThread (igual que hacía el código legacy
        // con ServerConnectionThread, pero ahora Retrofit gestiona el
        // hilo de fondo por nosotros, sin necesidad de una clase Thread
        // propia ni de thread.join() bloqueante).
        runOnUiThread(() -> {
            tableProductos.removeAllViews();

            TableRow header = new TableRow(DashboardActivity.this);
            header.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
            header.addView(crearTextView("Nombre", true));
            header.addView(crearTextView("Nutri-Score", true));
            header.addView(crearTextView("Caducidad", true));
            tableProductos.addView(header);

            for (InventarioDto unidad : inventario) {
                TableRow row = new TableRow(DashboardActivity.this);
                row.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
                row.addView(crearTextView(unidad.nombreProducto, false));
                row.addView(crearTextView(unidad.nutriScore, false));
                row.addView(crearTextView(unidad.fechaCaducidad, false));
                tableProductos.addView(row);
            }
        });
    }

    private TextView crearTextView(String texto, boolean cabecera) {
        TextView textView = new TextView(DashboardActivity.this);
        textView.setText(texto);
        textView.setPadding(8, 8, 8, 8);
        if (cabecera) {
            textView.setTypeface(null, Typeface.BOLD);
        }
        textView.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));
        return textView;
    }

    private void iniciarComprobacionDeAlertas() {
        alertCheckRunnable = new Runnable() {
            @Override
            public void run() {
                comprobarAlertas();
                handler.postDelayed(this, 2000);
            }
        };
        handler.postDelayed(alertCheckRunnable, 2000);
    }

    private void comprobarAlertas() {
        RetrofitClient.getApi().listarRegistros("ALERTA").enqueue(new Callback<List<RegistroDto>>() {
            @Override
            public void onResponse(Call<List<RegistroDto>> call, Response<List<RegistroDto>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }
                List<RegistroDto> alertas = response.body();
                boolean hayAlertas = !alertas.isEmpty();
                if (hayAlertas) {
                    // El backend ya devuelve findByTipoRegistroOrderByFechaDesc:
                    // el primer elemento es la alerta más reciente.
                    alertaDescripcion = describirAlerta(alertas.get(0));
                }
                runOnUiThread(() ->
                        alertIndicator.setVisibility(hayAlertas ? View.VISIBLE : View.GONE));
            }

            @Override
            public void onFailure(Call<List<RegistroDto>> call, Throwable t) {
                Log.w(TAG, "No se pudieron comprobar las alertas", t);
            }
        });
    }

    private String describirAlerta(RegistroDto registro) {
        if (registro.sensorTipo == null) {
            return "Alerta en el frigorífico";
        }
        // OJO: comparar contra MAYÚSCULAS ("AGUA", no "agua") — ver nota
        // de contrato en SensorDto.
        switch (registro.sensorTipo) {
            case "AGUA":
                return "Agua detectada: revisar frigorífico";
            case "TEMPERATURA":
                return "Temperatura alta: revisar frigorífico";
            case "PUERTA":
                return "Puerta abierta: cerrar frigorífico";
            case "HUMEDAD":
                return "Humedad alta: revisar frigorífico";
            default:
                return "Alerta en el frigorífico";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(alertCheckRunnable);
    }
}
