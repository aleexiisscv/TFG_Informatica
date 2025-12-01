package com.example.smartfridge;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import Android.Logic.Producto;
import Android.Logic.Sensor;
import java.util.List;

public class DashboardActivity extends AppCompatActivity {

    private boolean stateRfid;
    private TableLayout tableProductos;
    private Switch switchRFID;
    private Button logoutButton;
    private Button statsButton;
    private Button createProductButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        tableProductos = findViewById(R.id.tableProductos);
        switchRFID = findViewById(R.id.switchRFID);
        logoutButton = findViewById(R.id.logoutButton);
        statsButton = findViewById(R.id.statsButton);
        createProductButton = findViewById(R.id.createProductButton);

        // Cargar productos en la tabla
        loadProductos();

        // Manejar el estado del RFID
        switchRFID.setOnCheckedChangeListener((buttonView, isChecked) -> {
            stateRfid = isChecked;
            //Sensor.setRFIDState(isChecked); // Método de la clase Sensor para manejar el estado del lector RFID
            String message = isChecked ? "RFID activado" : "RFID desactivado";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });

        // Botón de cerrar sesión
        logoutButton.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, RegisterActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        // Botón para estadísticas
        statsButton.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, StatsActivity.class);
            startActivity(intent);
        });

        // Botón para crear productos
        createProductButton.setOnClickListener(v -> {
            //Intent intent = new Intent(DashboardActivity.this, CreateProductActivity.class);
            //startActivity(intent);
        });
    }

    private void loadProductos() {
        tableProductos.removeAllViews(); // Limpiar la tabla antes de agregar nuevos datos

        // Obtener la lista de productos usando la clase Producto de Logic
        List<Producto> productos = Producto.obtenerProductos(); // Metodo existente en Logic.Producto

        for (Producto producto : productos) {
            TableRow row = new TableRow(this);

            TextView rfidTextView = new TextView(this);
            rfidTextView.setText(producto.getRfidTag());
            rfidTextView.setPadding(8, 8, 8, 8);

            TextView nombreTextView = new TextView(this);
            nombreTextView.setText(producto.getNombre());
            nombreTextView.setPadding(8, 8, 8, 8);

            /*TextView cantidadTextView = new TextView(this);
            cantidadTextView.setText(String.valueOf(producto.getCantidad()));
            cantidadTextView.setPadding(8, 8, 8, 8);
            */
            TextView nutriScoreTextView = new TextView(this);
            nutriScoreTextView.setText(producto.getNutriScore());
            nutriScoreTextView.setPadding(8, 8, 8, 8);

            row.addView(rfidTextView);
            row.addView(nombreTextView);
            //row.addView(cantidadTextView);
            row.addView(nutriScoreTextView);

            tableProductos.addView(row);
        }
    }
}

