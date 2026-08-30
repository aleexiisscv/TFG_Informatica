package com.example.smartfridge;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.smartfridge.api.RetrofitClient;
import com.example.smartfridge.api.dto.ProductoDto;
import com.example.smartfridge.api.dto.RegistroDto;
import com.example.smartfridge.ui.util.Fechas;
import com.example.smartfridge.ui.util.NutriScoreUi;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Estadisticas.
 *
 * <h3>Esta pantalla era el mayor punto de fallo del modulo Android</h3>
 * La version heredada:
 * <ol>
 *   <li>Apuntaba a un servlet legacy con IP incrustada
 *       ({@code http://192.168.116.180:8080/...databaseAction}) que ya
 *       no existe: la pantalla no podia funcionar.</li>
 *   <li>Llamaba a {@code thread.join()} desde el hilo principal, lo que
 *       bloquea la interfaz hasta que responda la red. Es la receta
 *       exacta de un ANR ("Application Not Responding").</li>
 *   <li><b>Inventaba los datos</b>: {@code processRegistro()} generaba
 *       el Nutri-Score con {@code Math.random()}. Las estadisticas que
 *       mostraba eran ficticias.</li>
 *   <li>Parseaba las fechas con {@code "MMM d, yyyy, h:mm:ss a"} en
 *       ingles, formato que el backend Spring Boot no emite.</li>
 * </ol>
 *
 * <h3>Como funciona ahora</h3>
 * Dos llamadas Retrofit asincronas: {@code GET /api/productos} para
 * conocer el Nutri-Score de cada producto y {@code GET /api/registros}
 * para el historico de movimientos y alertas. Se cruzan en el cliente
 * por {@code nombreProducto}.
 *
 * <p><b>Deuda tecnica reconocida</b>: ese cruce deberia hacerlo el
 * servidor. {@code RegistroResponse} transporta {@code nombreProducto}
 * pero no {@code nutriScore}, asi que el movil se ve obligado a
 * descargar el catalogo entero para completar el dato — exactamente el
 * mismo anti-patron que se elimino en el inventario cuando
 * {@code InventarioResponse} paso a devolver el JOIN ya resuelto.
 * Añadir {@code nutriScore} a {@code RegistroResponse} eliminaria una
 * peticion completa y el cruce manual. Se documenta aqui para la
 * siguiente iteracion del backend.</p>
 */
public class StatsActivity extends AppCompatActivity {

    private static final String TAG = "StatsActivity";
    private static final long DIAS_POR_DEFECTO = 30L;

    private TextView statsSince, statsEmpty, statsEntries, statsExits;
    private TextView alertTempCount, alertHumCount, alertWaterCount, alertDoorCount;
    private LinearLayout nutriScoreContainer;

    private long desdeMillis;

    /** Cache del catalogo: nombre de producto -> Nutri-Score. */
    private final Map<String, String> nutriPorProducto = new HashMap<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        statsSince = findViewById(R.id.statsSince);
        statsEmpty = findViewById(R.id.statsEmpty);
        statsEntries = findViewById(R.id.statsEntries);
        statsExits = findViewById(R.id.statsExits);
        alertTempCount = findViewById(R.id.alertTempCount);
        alertHumCount = findViewById(R.id.alertHumCount);
        alertWaterCount = findViewById(R.id.alertWaterCount);
        alertDoorCount = findViewById(R.id.alertDoorCount);
        nutriScoreContainer = findViewById(R.id.nutriScoreContainer);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        MaterialButton pickDateButton = findViewById(R.id.pickDateButton);
        pickDateButton.setOnClickListener(v -> elegirFecha());

        // Arranca con una ventana util por defecto en lugar de una
        // pantalla en blanco a la espera de que el usuario adivine que
        // tiene que elegir una fecha.
        desdeMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(DIAS_POR_DEFECTO);
        cargar();
    }

    private void elegirFecha() {
        MaterialDatePicker<Long> selector = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.stats_pick_date)
                .setSelection(desdeMillis)
                .build();
        selector.addOnPositiveButtonClickListener(seleccion -> {
            desdeMillis = seleccion;
            cargar();
        });
        selector.show(getSupportFragmentManager(), "selectorFecha");
    }

    private void cargar() {
        statsSince.setText(getString(R.string.stats_since, Fechas.fechaCorta(new Date(desdeMillis))));

        // Primero el catalogo (para poder resolver el Nutri-Score), y
        // solo despues los registros. Las dos peticiones se encadenan en
        // los callbacks; ninguna bloquea el hilo principal, a diferencia
        // del thread.join() de la version anterior.
        RetrofitClient.getApi().listarProductos().enqueue(new Callback<List<ProductoDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<ProductoDto>> call,
                                   @NonNull Response<List<ProductoDto>> response) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                nutriPorProducto.clear();
                if (response.isSuccessful() && response.body() != null) {
                    for (ProductoDto producto : response.body()) {
                        if (producto.nombre != null) {
                            nutriPorProducto.put(producto.nombre, producto.nutriScore);
                        }
                    }
                }
                cargarRegistros();
            }

            @Override
            public void onFailure(@NonNull Call<List<ProductoDto>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                Log.w(TAG, "No se pudo cargar el catalogo de productos", t);
                // Se continua igualmente: sin catalogo se pierden las
                // barras de Nutri-Score, pero movimientos y alertas
                // siguen siendo calculables. Degradacion parcial en vez
                // de pantalla vacia.
                cargarRegistros();
            }
        });
    }

    private void cargarRegistros() {
        // tipo = null hace que Retrofit omita el parametro ?tipo, con lo
        // que el backend devuelve TODOS los registros.
        RetrofitClient.getApi().listarRegistros(null).enqueue(new Callback<List<RegistroDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<RegistroDto>> call,
                                   @NonNull Response<List<RegistroDto>> response) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    calcular(response.body());
                } else {
                    Log.w(TAG, "Respuesta no exitosa al listar registros: HTTP " + response.code());
                    Snackbar.make(statsSince, R.string.common_network_error, Snackbar.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<RegistroDto>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                Log.e(TAG, "Fallo de red al listar registros", t);
                Snackbar.make(statsSince, R.string.common_network_error, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void calcular(List<RegistroDto> registros) {
        int entradas = 0;
        int salidas = 0;
        int alertasTemp = 0, alertasHum = 0, alertasAgua = 0, alertasPuerta = 0;
        // LinkedHashMap para que las barras salgan siempre en orden A..E
        // y no en el orden arbitrario de un HashMap.
        Map<String, Integer> porNutriScore = new LinkedHashMap<>();
        for (String letra : new String[]{"A", "B", "C", "D", "E"}) {
            porNutriScore.put(letra, 0);
        }
        int totalConNutri = 0;
        int considerados = 0;

        for (RegistroDto registro : registros) {
            Date fecha = Fechas.parseIso(registro.fecha);
            if (fecha == null || fecha.getTime() < desdeMillis) {
                continue;
            }
            considerados++;

            if ("ENTRADA".equals(registro.tipoRegistro)) {
                entradas++;
                String letra = NutriScoreUi.normalizar(nutriPorProducto.get(registro.nombreProducto));
                if (letra != null) {
                    porNutriScore.put(letra, porNutriScore.get(letra) + 1);
                    totalConNutri++;
                }
            } else if ("SALIDA".equals(registro.tipoRegistro)) {
                salidas++;
            } else if ("ALERTA".equals(registro.tipoRegistro)) {
                // Recordatorio: el backend serializa el enum TipoSensor
                // en MAYUSCULAS.
                if ("TEMPERATURA".equals(registro.sensorTipo)) {
                    alertasTemp++;
                } else if ("HUMEDAD".equals(registro.sensorTipo)) {
                    alertasHum++;
                } else if ("AGUA".equals(registro.sensorTipo)) {
                    alertasAgua++;
                } else if ("PUERTA".equals(registro.sensorTipo)) {
                    alertasPuerta++;
                }
            }
        }

        statsEmpty.setVisibility(considerados == 0 ? View.VISIBLE : View.GONE);

        statsEntries.setText(getString(R.string.stats_entries, entradas));
        statsExits.setText(getString(R.string.stats_exits, salidas));

        alertTempCount.setText(String.valueOf(alertasTemp));
        alertHumCount.setText(String.valueOf(alertasHum));
        alertWaterCount.setText(String.valueOf(alertasAgua));
        alertDoorCount.setText(String.valueOf(alertasPuerta));

        pintarBarras(porNutriScore, totalConNutri);
    }

    /**
     * Dibuja una barra proporcional por categoria de Nutri-Score.
     *
     * <p>Se representa como longitud y no como el texto "A: 42%" de la
     * version anterior porque una comparacion entre magnitudes se
     * resuelve visualmente mucho mas rapido que leyendo cinco numeros.
     * El porcentaje se conserva en texto al final de cada barra para
     * quien necesite el dato exacto y para los lectores de pantalla.</p>
     */
    private void pintarBarras(Map<String, Integer> porNutriScore, int total) {
        nutriScoreContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (Map.Entry<String, Integer> entrada : porNutriScore.entrySet()) {
            String letra = entrada.getKey();
            int cuenta = entrada.getValue();
            if (cuenta == 0) {
                // No se pintan categorias sin datos: cinco barras a cero
                // son ruido, no informacion.
                continue;
            }
            int porcentaje = total == 0 ? 0 : Math.round(cuenta * 100f / total);

            View fila = inflater.inflate(R.layout.item_stat_bar, nutriScoreContainer, false);

            TextView barLabel = fila.findViewById(R.id.barLabel);
            barLabel.setText(letra);
            barLabel.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, NutriScoreUi.colorFondo(letra))));
            barLabel.setTextColor(ContextCompat.getColor(this, NutriScoreUi.colorTexto(letra)));

            View barFill = fila.findViewById(R.id.barFill);
            barFill.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, NutriScoreUi.colorFondo(letra))));
            aplicarPeso(barFill, porcentaje);
            aplicarPeso(fila.findViewById(R.id.barRest), 100 - porcentaje);

            TextView barValue = fila.findViewById(R.id.barValue);
            barValue.setText(getString(R.string.sensor_value_percent, String.valueOf(porcentaje)));

            // Accesibilidad: una barra es informacion puramente visual.
            // La fila se lee como una frase unica ("Nutri-Score A: 42 por
            // ciento") en vez de dejar que el lector recorra la letra, la
            // barra vacia y el numero por separado.
            fila.setContentDescription(getString(R.string.a11y_barra_nutriscore, letra, porcentaje));

            nutriScoreContainer.addView(fila);
        }
    }

    private static void aplicarPeso(View vista, float peso) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) vista.getLayoutParams();
        params.weight = Math.max(peso, 0f);
        vista.setLayoutParams(params);
    }
}
