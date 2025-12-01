package com.example.smartfridge;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private boolean stateRfid;
    private TableLayout tableProductos;
    private Switch switchRFID;
    private Button logoutButton;
    private Button statsButton;
    private Button createProductButton;

    private Button sensorButton;

    private ImageButton alertBellButton;

    private Handler handler = new Handler();
    private Runnable alertCheckRunnable;
    public boolean alertaExist = true; // Estado inicial de la alerta

    private View alertIndicator;

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


        // Manejar el estado del RFID
        switchRFID.setOnCheckedChangeListener((buttonView, isChecked) -> {
            stateRfid = isChecked;
            //Sensor.setRFIDState(isChecked); // Método de la clase Sensor para manejar el estado del lector RFID
            String message = isChecked ? "Modo entrada de alimentos activado" : "Modo entrada de alimentos desactivado";
            cambiarModoEnServidor(isChecked);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });

        // Botón de cerrar sesión
        logoutButton.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        // Botón para estadísticas
        statsButton.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, StatsActivity.class);
            startActivity(intent);
        });

        sensorButton.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, SensorActivity.class);
            startActivity(intent);
        });

        // Botón para crear productos
        createProductButton.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, CreateProductActivity.class);
            startActivity(intent);
        });

        alertBellButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Toast.makeText(DashboardActivity.this, alertaDescripcion, Toast.LENGTH_LONG).show();
            }
        });
        loadProductTest();
        setupAlertChecker();
    }

    private void loadProductTest() {
        String url = "http://192.168.116.180:8080/ServerExampleUbicomp-1.0-SNAPSHOT/databaseAction";
        ServerConnectionThread.clase = "DashboardActivity";
        ServerConnectionThread thread = new ServerConnectionThread(this, url);
        try {
            thread.join();
        } catch (InterruptedException e) {
        }
    }

    private void loadAlertas() {
        String url = "http://192.168.116.180:8080/ServerExampleUbicomp-1.0-SNAPSHOT/databaseAction";
        ServerConnectionThread.clase = "DashboardActivity2";
        ServerConnectionThread thread = new ServerConnectionThread(this, url);
        try {
            thread.join();
        } catch (InterruptedException e) {
        }
    }

    public void setCombinedProductList(JSONArray jsonProductos, JSONArray jsonInventario) {
        runOnUiThread(new Runnable() { // Asegura que la actualización de la UI se haga en el hilo principal
            @Override
            public void run() {
                try {
                    tableProductos.removeAllViews(); // Limpiar la tabla antes de agregar nuevos datos

                    // Encabezado de la tabla
                    TableRow header = new TableRow(DashboardActivity.this);
                    header.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));

                    header.addView(createTextView("Nombre"));
                    header.addView(createTextView("Nutri-Score"));
                    header.addView(createTextView("Caducidad"));
                    tableProductos.addView(header);

                    // Crear un mapa para acceso rápido a los productos por RFID
                    HashMap<String, JSONObject> productoMap = new HashMap<>();
                    for (int i = 0; i < jsonProductos.length(); i++) {
                        JSONObject producto = jsonProductos.getJSONObject(i);
                        productoMap.put(producto.getString("rfid_tag"), producto);
                    }

                    // Procesar cada entrada de inventario y buscar los datos del producto
                    for (int j = 0; j < jsonInventario.length(); j++) {
                        JSONObject inventario = jsonInventario.getJSONObject(j);
                        String productoRFID = inventario.getString("producto_id");
                        JSONObject producto = productoMap.get(productoRFID);

                        if (producto != null) {
                            TableRow row = new TableRow(DashboardActivity.this);
                            row.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));

                            // Extracción de datos del producto relacionado
                            String nombre = producto.getString("nombre");
                            String nutriScore = producto.getString("nutri_score");
                            String fechaCaducidad = inventario.getString("fecha_caducidad");

                            // Creación de TextViews para cada columna
                            TextView nombreTextView = createTextView(nombre);
                            TextView nutriScoreTextView = createTextView(nutriScore);
                            TextView fechaCadTextView = createTextView(fechaCaducidad);

                            // Añadir TextViews al row
                            row.addView(nombreTextView);
                            row.addView(nutriScoreTextView);
                            row.addView(fechaCadTextView);

                            // Añadir fila a la tabla
                            tableProductos.addView(row);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private TextView createTextView(String text) {
        TextView textView = new TextView(DashboardActivity.this);
        textView.setText(text);
        textView.setPadding(8, 8, 8, 8);
        textView.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));
        return textView;
    }


    public void handleJsonResponse(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            if (jsonObject.has("productos")) {
                JSONArray jsonProductos = jsonObject.getJSONArray("productos");
                JSONArray jsonInventario = jsonObject.getJSONArray("inventarios");


                setCombinedProductList(jsonProductos, jsonInventario);
            }
            // Añade más secciones según sea necesario
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void handleAlertas(String jsonResponse) {
        boolean foundRecentAnomaly = false;  // Bandera para detectar anomalías recientes
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy, h:mm:ss a", Locale.ENGLISH);

        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            if (jsonObject.has("registros")) {
                JSONArray jsonRegistros = jsonObject.getJSONArray("registros");
                long oneMinuteAgo = System.currentTimeMillis() - 3660000;  // Tiempo actual menos un minuto

                for (int i = 0; i < jsonRegistros.length(); i++) {
                    JSONObject registro = jsonRegistros.getJSONObject(i);
                    String tipoRegistro = registro.getString("tipo_registro");
                    int idSensor = registro.getInt("id_sensor");
                    String fechaRegistro = registro.getString("fecha");

                    try {
                        Date registroDate = dateFormat.parse(fechaRegistro);


                        if ("anomalia".equals(tipoRegistro) && registroDate.getTime() > oneMinuteAgo) {
                            System.out.println("Anomalia encontrada en el último minuto");
                            foundRecentAnomaly = true;  // Encontró una anomalía reciente
                            processRegistro(tipoRegistro, idSensor);
                        }
                    } catch (java.text.ParseException e) {
                        System.out.println("Error al parsear la fecha del registro");
                    }
                }
            }

            // Actualizar la variable de alertaExist según si se encontraron anomalías recientes
            alertaExist = foundRecentAnomaly;
            if (!alertaExist) {
                alertaDescripcion = "No se detectan alertas nuevas";

            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void processRegistro(String tipoRegistro, int idSensor) {
        if ("anomalia".equals(tipoRegistro)) {
            if (idSensor == 1) {
                alertaExist = true;
                alertaDescripcion = "Agua dectectada: Revisar Frigorifico";
            } else if (idSensor == 3) {
                alertaExist = true;
                alertaDescripcion = "Temperatura alta: Revisar Frigorifico";
            } else if (idSensor == 4) {
                alertaExist = true;
                alertaDescripcion = "Puerta Abierta: Cerrar Frigorifico";
            } else if (idSensor == 2) {
                alertaExist = true;
                alertaDescripcion = "Humedad alta: Revisar Frigorifico";
            }
        }
    }


    private void addTextToRow(TableRow row, String text, boolean isHeader) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setPadding(8, 8, 8, 8);
        if (isHeader) {
            textView.setTypeface(null, Typeface.BOLD);
        }
        row.addView(textView);
    }

    private void setupAlertChecker() {
        alertCheckRunnable = new Runnable() {
            @Override
            public void run() {
                loadAlertas();
                if (alertaExist) {
                    alertIndicator.setVisibility(View.VISIBLE);
                } else {
                    alertIndicator.setVisibility(View.GONE);
                }
                handler.postDelayed(this, 2000); // Re-post the delay
            }
        };
        handler.postDelayed(alertCheckRunnable, 2000); // Start the initial delay
    }

    private void cambiarModoEnServidor(boolean isChecked) {
        String url = "http://192.168.116.180:8080/ServerExampleUbicomp-1.0-SNAPSHOT/cambiarModo"; // Reemplaza tu-dominio con la URL de tu servidor
        OkHttpClient client = new OkHttpClient();

        RequestBody formBody = new FormBody.Builder()
                .add("modo", isChecked ? "INSERTAR" : "ELIMINAR")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Error de conexión", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Modo cambiado a: " + responseData, Toast.LENGTH_SHORT).show());
                } else {
                    runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Error al cambiar modo", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }
}

