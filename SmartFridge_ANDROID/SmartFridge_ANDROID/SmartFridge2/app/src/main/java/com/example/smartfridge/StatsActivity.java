package com.example.smartfridge;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;



import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class StatsActivity extends AppCompatActivity {

    private SimpleDateFormat dateFormat = new SimpleDateFormat("d-M-yy", Locale.ENGLISH);
    private SimpleDateFormat dateFormat2 = new SimpleDateFormat("MMM d, yyyy, h:mm:ss a", Locale.ENGLISH);
    private JSONArray jsonRegistro=null;


    private void showDatePicker() {
        // Obtener la fecha actual para mostrar en el DatePicker inicialmente
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);


        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                new DatePickerDialog.OnDateSetListener() {

                    @Override
                    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                        // Esta función se llama cuando se selecciona una fecha
                        String selectedDate = dayOfMonth + "-" + (monthOfYear + 1) + "-" + year;
                        // Aquí puedes llamar a una función para cargar las estadísticas desde la fecha seleccionada
                        loadEstaditicas();
                        loadStatisticsFromDate(selectedDate,jsonRegistro);
                    }
                }, year, month, day);
        datePickerDialog.show();
    }

    public void loadStatisticsFromDate(String date, JSONArray registros) {
        Date referenceDate;
        System.out.println("#################"+ registros.toString() );
        try {
            referenceDate = dateFormat.parse(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return; // Early return si la fecha no es válida
        }

        Map<Character, Integer> nutriScoreStats = new HashMap<>();
        Map<String, Integer> alertStats = new HashMap<>();
        alertStats.put("agua", 0);
        alertStats.put("temperatura", 0);

        for (int i = 0; i < registros.length(); i++) {
            try {
                JSONObject registro = registros.getJSONObject(i);
                Date registroDate = dateFormat2.parse(registro.getString("fecha"));
                if (registroDate != null && registroDate.after(referenceDate)) {
                    String tipoRegistro = registro.getString("tipo_registro");
                    int idSensor = registro.getInt("id_sensor");
                    processRegistro(tipoRegistro, idSensor, nutriScoreStats, alertStats);
                }
            } catch (JSONException | ParseException e) {
                e.printStackTrace();
            }
        }

        mostrarEstadisticas(nutriScoreStats);
        mostrarEstadisticasDeAlertas(alertStats);

        Toast.makeText(this, "Estadísticas desde: " + date, Toast.LENGTH_LONG).show();
    }

    private void processRegistro(String tipoRegistro, int idSensor, Map<Character, Integer> nutriScoreStats, Map<String, Integer> alertStats) {
        if ("entrada".equals(tipoRegistro)) {
            // Aquí necesitarías alguna forma de obtener el Nutri-Score del producto referenciado por id_producto
            // Simulamos algunos datos
            char nutriScore = (char) ('A' + (Math.random() * ('E' - 'A'))); // Simulación
            nutriScoreStats.put(nutriScore, nutriScoreStats.getOrDefault(nutriScore, 0) + 1);
        } else if ("anomalia".equals(tipoRegistro)) {
            if (idSensor == 1) {
                alertStats.put("agua", alertStats.get("agua") + 1);
            } else if (idSensor == 3) {
                alertStats.put("temperatura", alertStats.get("temperatura") + 1);
            }
        }
    }

    public void mostrarEstadisticas(Map<Character, Integer> estadisticas) {
        TextView textViewNutriScores = findViewById(R.id.nutriScoreStats);

        int total = estadisticas.values().stream().mapToInt(Integer::intValue).sum();
        StringBuilder resultado = new StringBuilder();

        estadisticas.entrySet().stream()
                .sorted(Map.Entry.<Character, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    int porcentaje = (int) ((entry.getValue() * 100.0) / total);
                    resultado.append(entry.getKey()).append(": ").append(porcentaje).append("%\n");
                });

        textViewNutriScores.setText(resultado.toString());
    }
    private void loadEstaditicas(){
        String url = "http://192.168.116.180:8080/ServerExampleUbicomp-1.0-SNAPSHOT/databaseAction";
        ServerConnectionThread.clase = "StatsActivity";
        ServerConnectionThread thread = new ServerConnectionThread(this, url);
        try {
            thread.join();
        }catch (InterruptedException e){}
    }
    public void handleJsonResponse(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            if (jsonObject.has("registros")) {
                jsonRegistro = jsonObject.getJSONArray("registros");
                //setSensores(jsonSensores);
                System.out.println("AAAAAAAAAAAAAAAAAAAA"+jsonRegistro.toString());

            }
            // Añade más secciones según sea necesario
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void mostrarEstadisticasDeAlertas(Map<String, Integer> estadisticas) {
        TextView tempAlertsCount = findViewById(R.id.tempAlertsStats);
        TextView waterAlertsCount = findViewById(R.id.waterAlertsCount);

        // Asumiendo que "temperatura" y "agua" son las claves en el mapa de estadísticas
        Integer countTemp = estadisticas.getOrDefault("temperatura", 0);
        Integer countWater = estadisticas.getOrDefault("agua", 0);

        tempAlertsCount.setText("Total alertas de temperatura: " + countTemp);
        waterAlertsCount.setText("Total alertas de agua: " + countWater);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        // Inicializar vistas
        Button calculateButton = findViewById(R.id.calculateButton);
        Button backButton = findViewById(R.id.backButton);
        TextView healthyEatingStats = findViewById(R.id.healthyEatingStats);
        TextView temperatureAlertsStats = findViewById(R.id.temperatureAlertsStats);
        TextView waterAlertsStats = findViewById(R.id.waterAlertsStats);

        // Botón para calcular estadísticas
        calculateButton.setOnClickListener(v -> {
            showDatePicker();
            // Estadísticas de Nutri-Scores
            /*Map<Character, Integer> entradasPorNutriScore = Registro.obtenerEstadisticasEntradasPorNutriScore();
            Map<Character, Integer> salidasPorNutriScore = Registro.obtenerEstadisticasSalidasPorNutriScore();
            if (entradasPorNutriScore != null && salidasPorNutriScore != null) {
                StringBuilder healthyStats = new StringBuilder("Nutri-Scores:\nEntradas:\n");
                for (Map.Entry<Character, Integer> entry : entradasPorNutriScore.entrySet()) {
                    healthyStats.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
                healthyStats.append("Salidas:\n");
                for (Map.Entry<Character, Integer> entry : salidasPorNutriScore.entrySet()) {
                    healthyStats.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
                healthyEatingStats.setText(healthyStats.toString());
            } else {
                healthyEatingStats.setText("No se encontraron datos de Nutri-Scores.");
            }

            // Estadísticas de alertas por tipo de sensor
            Map<String, Integer> alertasPorTipo = Registro.obtenerEstadisticasAlertasPorTipoSensor();
            if (alertasPorTipo != null) {
                for (Map.Entry<String, Integer> entry : alertasPorTipo.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase("temperatura")) {
                        temperatureAlertsStats.setText("Alertas de temperatura: " + entry.getValue());
                    } else if (entry.getKey().equalsIgnoreCase("agua")) {
                        waterAlertsStats.setText("Alertas de agua: " + entry.getValue());
                    }
                }
            } else {
                temperatureAlertsStats.setText("No se encontraron alertas de temperatura.");
                waterAlertsStats.setText("No se encontraron alertas de agua.");
            }*/
        });

        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(StatsActivity.this, DashboardActivity.class);
            startActivity(intent);
            finish();
        });
    }
}