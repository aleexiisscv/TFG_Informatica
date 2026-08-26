package com.example.smartfridge.ui.sensores;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.smartfridge.R;
import com.example.smartfridge.api.RetrofitClient;
import com.example.smartfridge.api.dto.RegistroDto;
import com.example.smartfridge.api.dto.SensorDto;
import com.example.smartfridge.ui.util.Fechas;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Seccion "Sensores y alertas". Sucesora de {@code SensorActivity}.
 *
 * <h3>Decisiones</h3>
 * <ul>
 *   <li><b>Sondeo cada 5 s, no cada 2 s</b>, y detenido en
 *       {@code onPause} en lugar de en {@code onDestroy}. La version
 *       heredada seguia pidiendo datos con la pantalla apagada.</li>
 *   <li><b>Cada sensor tiene su tarjeta</b> con un
 *       {@code anomalyBadge} oculto. Ese indicador esta pensado para
 *       que el modulo de deteccion de anomalias lo encienda: la
 *       interfaz ya reserva el sitio, de modo que integrar la IA sera
 *       una llamada a {@code marcarAnomalia()} y no un rediseño.</li>
 *   <li><b>Las alertas se listan</b>, no se resumen en un Toast como
 *       hacia {@code alertBellButton}: un Toast desaparece y no deja
 *       consultar el historico reciente.</li>
 * </ul>
 */
public class SensoresFragment extends Fragment {

    private static final String TAG = "SensoresFragment";
    private static final long INTERVALO_SENSORES_MS = 5_000L;
    private static final int MAX_ALERTAS_VISIBLES = 5;

    private SwipeRefreshLayout swipeRefresh;
    private TextView lastUpdate;
    private TextView tempValue, humValue, waterValue, doorValue;
    private ImageView tempAnomaly, humAnomaly, waterAnomaly, doorAnomaly;
    private RecyclerView alertList;
    private TextView alertsEmpty;
    private AlertaAdapter alertaAdapter;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable tareaSondeo;
    @Nullable
    private Call<List<SensorDto>> llamadaSensores;
    @Nullable
    private Call<List<RegistroDto>> llamadaAlertas;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sensores, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        lastUpdate = view.findViewById(R.id.lastUpdate);

        tempValue = view.findViewById(R.id.tempValue);
        humValue = view.findViewById(R.id.humValue);
        waterValue = view.findViewById(R.id.waterValue);
        doorValue = view.findViewById(R.id.doorValue);

        tempAnomaly = view.findViewById(R.id.tempAnomaly);
        humAnomaly = view.findViewById(R.id.humAnomaly);
        waterAnomaly = view.findViewById(R.id.waterAnomaly);
        doorAnomaly = view.findViewById(R.id.doorAnomaly);

        alertList = view.findViewById(R.id.alertList);
        alertsEmpty = view.findViewById(R.id.alertsEmpty);

        alertaAdapter = new AlertaAdapter();
        alertList.setAdapter(alertaAdapter);

        swipeRefresh.setOnRefreshListener(this::refrescar);
    }

    @Override
    public void onResume() {
        super.onResume();
        tareaSondeo = new Runnable() {
            @Override
            public void run() {
                refrescar();
                handler.postDelayed(this, INTERVALO_SENSORES_MS);
            }
        };
        handler.post(tareaSondeo);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (tareaSondeo != null) {
            handler.removeCallbacks(tareaSondeo);
        }
        cancelarLlamadas();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelarLlamadas();
        alertList.setAdapter(null);
    }

    private void cancelarLlamadas() {
        if (llamadaSensores != null) {
            llamadaSensores.cancel();
            llamadaSensores = null;
        }
        if (llamadaAlertas != null) {
            llamadaAlertas.cancel();
            llamadaAlertas = null;
        }
    }

    private void refrescar() {
        cargarSensores();
        cargarAlertas();
    }

    private void cargarSensores() {
        llamadaSensores = RetrofitClient.getApi().listarSensores();
        llamadaSensores.enqueue(new Callback<List<SensorDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<SensorDto>> call,
                                   @NonNull Response<List<SensorDto>> response) {
                if (getView() == null) {
                    return;
                }
                swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    pintarSensores(response.body());
                } else {
                    Log.w(TAG, "Respuesta no exitosa al listar sensores: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<SensorDto>> call, @NonNull Throwable t) {
                if (call.isCanceled() || getView() == null) {
                    return;
                }
                swipeRefresh.setRefreshing(false);
                Log.w(TAG, "No se pudo obtener el estado de los sensores", t);
            }
        });
    }

    private void cargarAlertas() {
        llamadaAlertas = RetrofitClient.getApi().listarRegistros("ALERTA");
        llamadaAlertas.enqueue(new Callback<List<RegistroDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<RegistroDto>> call,
                                   @NonNull Response<List<RegistroDto>> response) {
                if (getView() == null || !response.isSuccessful() || response.body() == null) {
                    return;
                }
                pintarAlertas(response.body());
            }

            @Override
            public void onFailure(@NonNull Call<List<RegistroDto>> call, @NonNull Throwable t) {
                if (call.isCanceled()) {
                    return;
                }
                Log.w(TAG, "No se pudieron cargar las alertas", t);
            }
        });
    }

    private void pintarSensores(List<SensorDto> sensores) {
        String ultima = null;
        for (SensorDto sensor : sensores) {
            if (sensor.tipo == null) {
                continue;
            }
            switch (sensor.tipo) {
                case "TEMPERATURA":
                    tempValue.setText(getString(R.string.sensor_value_celsius, formatear(sensor.medicion)));
                    break;
                case "HUMEDAD":
                    humValue.setText(getString(R.string.sensor_value_percent, formatear(sensor.medicion)));
                    break;
                case "AGUA":
                    waterValue.setText(esUno(sensor.medicion)
                            ? R.string.sensor_water_detected : R.string.sensor_water_clear);
                    // El sensor de agua es binario y su estado "1" YA es
                    // una anomalia por definicion: no hace falta esperar
                    // a un modelo para marcarlo.
                    marcarAnomalia(waterAnomaly, esUno(sensor.medicion));
                    break;
                case "PUERTA":
                    doorValue.setText(esUno(sensor.medicion)
                            ? R.string.sensor_door_open : R.string.sensor_door_closed);
                    marcarAnomalia(doorAnomaly, esUno(sensor.medicion));
                    break;
                default:
                    Log.w(TAG, "Tipo de sensor no reconocido: " + sensor.tipo);
                    break;
            }
            if (sensor.ultLectura != null) {
                ultima = sensor.ultLectura;
            }
        }
        lastUpdate.setText(ultima == null
                ? getString(R.string.sensor_never_updated)
                : getString(R.string.sensor_updated_at, Fechas.fechaHora(ultima)));
    }

    private void pintarAlertas(List<RegistroDto> alertas) {
        // El backend ya devuelve findByTipoRegistroOrderByFechaDesc, asi
        // que basta con recortar: la mas reciente va primero.
        // Se copia a una lista nueva en lugar de pasar el subList: un
        // subList es una VISTA sobre la lista original, y ListAdapter
        // conserva la referencia entre refrescos. Si la original cambia,
        // el adaptador quedaria comparando contra datos ya mutados.
        List<RegistroDto> visibles = new ArrayList<>(
                alertas.size() > MAX_ALERTAS_VISIBLES
                        ? alertas.subList(0, MAX_ALERTAS_VISIBLES)
                        : alertas);
        alertaAdapter.submitList(visibles);
        alertsEmpty.setVisibility(visibles.isEmpty() ? View.VISIBLE : View.GONE);
        alertList.setVisibility(visibles.isEmpty() ? View.GONE : View.VISIBLE);

        // Reflejo de las alertas de temperatura/humedad sobre sus
        // tarjetas. Cuando exista el detector de anomalias basado en la
        // serie temporal (tabla lectura_sensor), esta misma llamada
        // recibira su veredicto en lugar del registro puntual.
        boolean hayTemp = false;
        boolean hayHum = false;
        for (RegistroDto alerta : visibles) {
            if ("TEMPERATURA".equals(alerta.sensorTipo)) {
                hayTemp = true;
            } else if ("HUMEDAD".equals(alerta.sensorTipo)) {
                hayHum = true;
            }
        }
        marcarAnomalia(tempAnomaly, hayTemp);
        marcarAnomalia(humAnomaly, hayHum);
    }

    /**
     * Punto de entrada unico para encender/apagar el indicador de
     * anomalia de una tarjeta. Se deja publico a nivel de paquete a
     * proposito: es la costura por la que entrara el modulo de IA.
     */
    void marcarAnomalia(ImageView badge, boolean hayAnomalia) {
        badge.setVisibility(hayAnomalia ? View.VISIBLE : View.GONE);
    }

    private String formatear(@Nullable Float medicion) {
        return medicion == null ? getString(R.string.sensor_no_data) : String.valueOf(medicion);
    }

    private boolean esUno(@Nullable Float medicion) {
        return medicion != null && medicion == 1.0f;
    }
}
