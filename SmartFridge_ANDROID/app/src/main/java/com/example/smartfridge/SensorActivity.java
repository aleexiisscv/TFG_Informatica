package com.example.smartfridge;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SensorActivity extends AppCompatActivity {

    private TextView waterSensor, tempSensor, humiditySensor, doorSensor;
    private Button backButton;
    private final Handler handler = new Handler();
    private Runnable updateSensors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensors);

        // Enlaza las vistas
        waterSensor = findViewById(R.id.waterSensor);
        tempSensor = findViewById(R.id.tempSensor);
        humiditySensor = findViewById(R.id.humiditySensor);
        doorSensor = findViewById(R.id.doorSensor);
        backButton = findViewById(R.id.backButton);

        // Configura el botón para volver a la actividad principal
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(SensorActivity.this, DashboardActivity.class);
            startActivity(intent);
            finish();
        });

        // Inicia la actualización de los sensores
        startSensorUpdates();
    }

    private void startSensorUpdates() {
        updateSensors = new Runnable() {
            @Override
            public void run() {
                // Aquí iría la lógica para obtener los datos del servidor, por ahora valores de ejemplo
                //updateSensorValues();
                loadSensores();

                // Re-post the delay to update sensors every 2 seconds
                handler.postDelayed(this, 2000);
            }
        };
        handler.postDelayed(updateSensors, 2000);
    }

    private void updateSensorValues() {
        // Estos valores deberían ser actualizados con los datos reales del servidor
        waterSensor.setText("Sensor de Agua: " + (Math.random() > 0.5 ? "1.0" : "0.0"));
        tempSensor.setText("Temperatura: " + (20 + (int)(Math.random() * 10)) + "ºC");
        humiditySensor.setText("Humedad: " + (50 + (int)(Math.random() * 50)) + "%");
        doorSensor.setText("Puerta Abierta: " + (Math.random() > 0.5 ? "1.0" : "0.0"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove callbacks to avoid memory leaks
        handler.removeCallbacks(updateSensors);
    }
    public void handleJsonResponse(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            if (jsonObject.has("sensores")) {
                JSONArray jsonSensores = jsonObject.getJSONArray("sensores");
                setSensores(jsonSensores);
                System.out.println("AAAAAAAAAAAAA");

            }
            // Añade más secciones según sea necesario
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    public void setSensores(JSONArray jsonSensores) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("#############"+jsonSensores.toString());
                    for (int i = 0; i < jsonSensores.length(); i++) {
                        JSONObject sensor = jsonSensores.getJSONObject(i);
                        String tipo = sensor.getString("tipo");
                        double medicion = sensor.getDouble("medicion");
                        String ultimaLectura = sensor.getString("ult_lectura");

                        switch (tipo) {
                            case "agua":
                                waterSensor.setText("Sensor de Agua: " + (medicion == 1.0 ? "Detectada" : "No Detectada"));
                                break;
                            case "temperatura":
                                tempSensor.setText("Temperatura: " + medicion + "ºC");
                                break;
                            case "humedad":
                                humiditySensor.setText("Humedad: " + medicion + "%");
                                break;
                            case "puerta":
                                doorSensor.setText("Puerta Abierta: " + (medicion == 1.0 ? "Sí" : "No"));
                                break;
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }
    private void loadSensores(){
        String url = "http://192.168.116.180:8080/ServerExampleUbicomp-1.0-SNAPSHOT/databaseAction";
        ServerConnectionThread.clase = "SensorActivity";
        ServerConnectionThread thread = new ServerConnectionThread(this, url);
        try {
            thread.join();
        }catch (InterruptedException e){}
    }
}
