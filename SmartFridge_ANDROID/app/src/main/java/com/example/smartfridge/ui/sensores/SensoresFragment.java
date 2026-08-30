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
import androidx.annotation.StringRes;
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
 * <h2>Decisiones</h2>
 * <ul>
 *   <li><b>Sondeo cada 5 s, no cada 2 s</b>, y detenido en
 *       {@code onPause} en lugar de en {@code onDestroy}. La version
 *       heredada seguia pidiendo datos con la pantalla apagada.</li>
 *   <li><b>Cada sensor tiene su tarjeta</b> con un
 *       {@code anomalyBadge} oculto, reservado al modulo de deteccion de
 *       anomalias.</li>
 *   <li><b>Las alertas se listan</b>, no se resumen en un Toast: un Toast
 *       desaparece y no deja consultar el historico reciente.</li>
 * </ul>
 *
 * <h2>Accesibilidad (Fase 12)</h2>
 * Cada tarjeta es UN solo punto de parada para el lector de pantalla, con
 * una frase completa: <i>"Temperatura interior: 4.2 grados. Estado
 * normal"</i>. Sin agrupar, TalkBack se detendria tres veces por sensor
 * —icono, etiqueta y valor— y "4.2" leido suelto no significa nada.
 *
 * <p>Y sobre todo: el indicador de anomalia es un icono ROJO. Quien no
 * distingue el rojo, o no ve la pantalla, no percibiria la alerta de
 * ninguna forma. Repetir ese estado en la descripcion es lo que exige el
 * criterio 1.4.1 de la WCAG: el color nunca puede ser el unico medio para
 * transmitir informacion.
 */
public class SensoresFragment extends Fragment {

    private static final String TAG = "SensoresFragment";
    private static final long INTERVALO_SENSORES_MS = 5_000L;
    private static final int MAX_ALERTAS_VISIBLES = 5;

    private SwipeRefreshLayout swipeRefresh;
    private TextView lastUpdate;
    private TextView tempValue, humValue, waterValue, doorValue;
    private ImageView tempAnomaly, humAnomaly, waterAnomaly, doorAnomaly;
    private View tempCard, humCard, waterCard, doorCard;
    private RecyclerView alertList;
    private TextView alertsEmpty;
    private AlertaAdapter alertaAdapter;

    /**
     * Ultimo valor y estado de cada sensor, para poder recomponer las
     * descripciones habladas. Hacen falta porque el valor llega de
     * /api/sensores y la anomalia de /api/registros: son dos respuestas
     * distintas y la frase que oye el usuario necesita las dos.
     */
    private CharSequence valorTemp, valorHum, valorAgua, valorPuerta;
    private boolean anomTemp, anomHum, anomAgua, anomPuerta;

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

        tempCard = view.findViewById(R.id.tempCard);
        humCard = view.findViewById(R.id.humCard);
        waterCard = view.findViewById(R.id.waterCard);
        doorCard = view.findViewById(R.id.doorCard);

        alertList = view.findViewById(R.id.alertList);
        alertsEmpty = view.findViewById(R.id.alertsEmpty);

        String sinDato = getString(R.string.sensor_no_data);
        valorTemp = sinDato;
        valorHum = sinDato;
        valorAgua = sinDato;
        valorPuerta = sinDato;
        refrescarDescripciones();

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
                    valorTemp = getString(R.string.sensor_value_celsius, formatear(sensor.medicion));
                    tempValue.setText(valorTemp);
                    break;
                case "HUMEDAD":
                    valorHum = getString(R.string.sensor_value_percent, formatear(sensor.medicion));
                    humValue.setText(valorHum);
                    break;
                case "AGUA":
                    valorAgua = getString(esUno(sensor.medicion)
                            ? R.string.sensor_water_detected : R.string.sensor_water_clear);
                    waterValue.setText(valorAgua);
                    // El sensor de agua es binario y su estado "1" YA es
                    // una anomalia por definicion: no hace falta esperar
                    // a un modelo para marcarlo.
                    anomAgua = esUno(sensor.medicion);
                    marcarAnomalia(waterAnomaly, anomAgua);
                    break;
                case "PUERTA":
                    valorPuerta = getString(esUno(sensor.medicion)
                            ? R.string.sensor_door_open : R.string.sensor_door_closed);
                    doorValue.setText(valorPuerta);
                    anomPuerta = esUno(sensor.medicion);
                    marcarAnomalia(doorAnomaly, anomPuerta);
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
        refrescarDescripciones();
    }

    private void pintarAlertas(List<RegistroDto> alertas) {
        // Se copia a una lista nueva en lugar de pasar el subList: un
        // subList es una VISTA sobre la lista original, y ListAdapter
        // conserva la referencia entre refrescos.
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
        anomTemp = false;
        anomHum = false;
        for (RegistroDto alerta : visibles) {
            if ("TEMPERATURA".equals(alerta.sensorTipo)) {
                anomTemp = true;
            } else if ("HUMEDAD".equals(alerta.sensorTipo)) {
                anomHum = true;
            }
        }
        marcarAnomalia(tempAnomaly, anomTemp);
        marcarAnomalia(humAnomaly, anomHum);
        refrescarDescripciones();
    }

    /**
     * Punto de entrada unico para encender/apagar el indicador de
     * anomalia de una tarjeta. Se deja visible a nivel de paquete a
     * proposito: es la costura por la que entrara el modulo de IA.
     */
    void marcarAnomalia(ImageView badge, boolean hayAnomalia) {
        badge.setVisibility(hayAnomalia ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------
    // Accesibilidad
    // ------------------------------------------------------------------

    private void refrescarDescripciones() {
        if (getView() == null) {
            return;
        }
        describir(tempCard, R.string.sensor_temperature, valorTemp, anomTemp);
        describir(humCard, R.string.sensor_humidity, valorHum, anomHum);
        describir(waterCard, R.string.sensor_water, valorAgua, anomAgua);
        describir(doorCard, R.string.sensor_door, valorPuerta, anomPuerta);
    }

    /**
     * Compone la frase que lee el lector de pantalla para una tarjeta.
     *
     * <p>Incluye SIEMPRE el estado, tambien cuando es normal. Anunciar la
     * anomalia solo cuando existe obligaria al usuario a recordar que la
     * ausencia de frase significa "todo bien", lo que es justo lo que no
     * se puede pedir a quien no ve la pantalla.</p>
     */
    private void describir(View tarjeta, @StringRes int etiqueta, CharSequence valor, boolean anomalia) {
        if (tarjeta == null) {
            return;
        }
        tarjeta.setContentDescription(getString(R.string.a11y_sensor,
                getString(etiqueta),
                valor == null ? getString(R.string.sensor_no_data) : valor,
                getString(anomalia ? R.string.a11y_estado_anomalia : R.string.a11y_estado_normal)));
    }

    private String formatear(@Nullable Float medicion) {
        return medicion == null ? getString(R.string.sensor_no_data) : String.valueOf(medicion);
    }

    private boolean esUno(@Nullable Float medicion) {
        return medicion != null && medicion == 1.0f;
    }
}
