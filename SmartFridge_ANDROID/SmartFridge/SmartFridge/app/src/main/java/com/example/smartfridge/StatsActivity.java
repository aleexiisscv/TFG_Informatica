package com.example.smartfridge;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import Android.Logic.Registro;
import java.util.Map;

public class StatsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        // Inicializar vistas
        Button calculateButton = findViewById(R.id.calculateButton);
        TextView healthyEatingStats = findViewById(R.id.healthyEatingStats);
        TextView temperatureAlertsStats = findViewById(R.id.temperatureAlertsStats);
        TextView waterAlertsStats = findViewById(R.id.waterAlertsStats);

        // Botón para calcular estadísticas
        calculateButton.setOnClickListener(v -> {
            // Estadísticas de Nutri-Scores
            Map<Character, Integer> entradasPorNutriScore = Registro.obtenerEstadisticasEntradasPorNutriScore();
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
            }
        });
    }
}