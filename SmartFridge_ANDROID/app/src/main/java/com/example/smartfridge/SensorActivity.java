package com.example.smartfridge;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import com.example.smartfridge.api.RetrofitClient;
import com.example.smartfridge.api.dto.SensorDto;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Refactorizada para consumir GET /api/sensores vía Retrofit, con el
 * mismo patrón de refresco cada 2 segundos que ya tenía (Handler +
 * postDelayed), pero sustituyendo ServerConnectionThread por una
 * llamada Retrofit asíncrona.
 *
 * CAMBIO DE CONTRATO IMPORTANTE: el backend legacy guardaba "tipo"
 * como texto libre en minúsculas ("agua", "temperatura", "humedad",
 * "puerta"). El nuevo backend usa el enum TipoSensor, que Jackson
 * serializa como "AGUA", "TEMPERATURA", "HUMEDAD", "PUERTA" (en
 * MAYÚSCULAS). El switch de abajo compara contra los valores nuevos.
 */
public class SensorActivity extends AppCompatActivity {

    private static final String TAG = "SensorActivity";

    private TextView waterSensor, tempSensor, humiditySensor, doorSensor;
    private Button backButton;
    private final Handler handler = new Handler();
    private Runnable updateSensors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensors);

        waterSensor = findViewById(R.id.waterSensor);
        tempSensor = findViewById(R.id.tempSensor);
        humiditySensor = findViewById(R.id.humiditySensor);
        doorSensor = findViewById(R.id.doorSensor);
        backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> {
            startActivity(new Intent(SensorActivity.this, DashboardActivity.class));
            finish();
        });

        startSensorUpdates();
    }

    private void startSensorUpdates() {
        updateSensors = new Runnable() {
            @Override
            public void run() {
                cargarSensores();
                handler.postDelayed(this, 2000);
            }
        };
        handler.postDelayed(updateSensors, 2000);
    }

    private void cargarSensores() {
        RetrofitClient.getApi().listarSensores().enqueue(new Callback<List<SensorDto>>() {
            @Override
            public void onResponse(Call<List<SensorDto>> call, Response<List<SensorDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    pintarSensores(response.body());
                } else {
                    Log.w(TAG, "Respuesta no exitosa al listar sensores: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<SensorDto>> call, Throwable t) {
                Log.w(TAG, "No se pudo obtener el estado de los sensores", t);
            }
        });
    }

    private void pintarSensores(List<SensorDto> sensores) {
        runOnUiThread(() -> {
            for (SensorDto sensor : sensores) {
                if (sensor.tipo == null) {
                    continue;
                }
                switch (sensor.tipo) {
                    case "AGUA":
                        waterSensor.setText("Sensor de Agua: " + (esUno(sensor.medicion) ? "Detectada" : "No Detectada"));
                        break;
                    case "TEMPERATURA":
                        tempSensor.setText("Temperatura: " + sensor.medicion + "ºC");
                        break;
                    case "HUMEDAD":
                        humiditySensor.setText("Humedad: " + sensor.medicion + "%");
                        break;
                    case "PUERTA":
                        doorSensor.setText("Puerta Abierta: " + (esUno(sensor.medicion) ? "Sí" : "No"));
                        break;
                    default:
                        Log.w(TAG, "Tipo de sensor no reconocido: " + sensor.tipo);
                }
            }
        });
    }

    private boolean esUno(Float medicion) {
        return medicion != null && medicion == 1.0f;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Igual que en la versión legacy: evita fugas de memoria y
        // llamadas de red huérfanas tras cerrar la pantalla.
        handler.removeCallbacks(updateSensors);
    }
}
