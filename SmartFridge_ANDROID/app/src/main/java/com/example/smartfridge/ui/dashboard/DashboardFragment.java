package com.example.smartfridge.ui.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.smartfridge.CreateProductActivity;
import com.example.smartfridge.LoginActivity;
import com.example.smartfridge.R;
import com.example.smartfridge.ScanProductActivity;
import com.example.smartfridge.StatsActivity;
import com.example.smartfridge.api.RetrofitClient;
import com.example.smartfridge.api.dto.InventarioDto;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Seccion "Inventario". Sucesora de {@code DashboardActivity}.
 *
 * <h3>Cambios funcionales</h3>
 * <ul>
 *   <li><b>Estados explicitos.</b> La pantalla distingue ahora tres
 *       situaciones que antes se veian identicas (tabla en blanco):
 *       cargando, vacio y error de red. Es la correccion de usabilidad
 *       mas importante de esta fase.</li>
 *   <li><b>Refresco bajo demanda</b> (deslizar hacia abajo) en lugar de
 *       un sondeo temporizado. El inventario cambia cuando el usuario
 *       mete o saca algo, no cada 2 s.</li>
 *   <li><b>Se elimina el Switch RFID.</b> No estaba conectado a nada
 *       —el modo del frigorifico solo se cambia publicando en el topic
 *       MQTT {@code frigorifico/modo}, sin endpoint REST equivalente— y
 *       ademas el proyecto ha descartado el RFID como identificador
 *       fisico. Un control que no hace nada es peor que su ausencia:
 *       enseña al usuario que la app miente.</li>
 *   <li><b>Se elimina la campana de alerta local.</b> Las alertas son
 *       globales y ahora viven como badge sobre la pestaña "Sensores"
 *       (ver {@code MainShellActivity}).</li>
 * </ul>
 *
 * <h3>Ciclo de vida</h3>
 * La llamada Retrofit en vuelo se cancela en {@code onDestroyView} y
 * todos los callbacks comprueban {@code getView() != null} antes de
 * tocar la interfaz. Sin eso, una respuesta que llega despues de que el
 * fragmento se haya destruido provoca un
 * {@code NullPointerException} o un {@code IllegalStateException} — el
 * fallo intermitente clasico de una app que hace red sin atender al
 * ciclo de vida.
 */
public class DashboardFragment extends Fragment {

    private static final String TAG = "DashboardFragment";

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView inventarioList;
    private View emptyState;
    private View errorState;
    private TextView countChip;
    private InventarioAdapter adapter;

    @Nullable
    private Call<List<InventarioDto>> llamadaEnCurso;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        inventarioList = view.findViewById(R.id.inventarioList);
        emptyState = view.findViewById(R.id.emptyState);
        errorState = view.findViewById(R.id.errorState);
        countChip = view.findViewById(R.id.countChip);

        adapter = new InventarioAdapter();
        inventarioList.setAdapter(adapter);
        // La altura de cada fila no depende del contenido: informarlo
        // permite a RecyclerView evitar un relayout completo por item.
        inventarioList.setHasFixedSize(true);

        swipeRefresh.setOnRefreshListener(this::cargarInventario);
        view.findViewById(R.id.retryButton).setOnClickListener(v -> cargarInventario());

        ExtendedFloatingActionButton scanFab = view.findViewById(R.id.scanFab);
        scanFab.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ScanProductActivity.class)));
        ajustarMargenSobreBarraInferior(scanFab);

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(this::onOpcionDeMenu);
    }

    /**
     * El FAB vivia con un {@code layout_marginBottom} fijo en XML (88dp)
     * que asumia una altura constante para la {@code BottomNavigationView}
     * del contenedor ({@code MainShellActivity}). Esa altura NO es
     * constante: con {@code labelVisibilityMode="labeled"} depende de la
     * escala de fuente y la densidad del dispositivo, y en varios
     * emuladores/telefonos supera el margen fijo — el FAB queda dibujado
     * detras de la barra inferior (que se pinta despues, y por tanto
     * encima) y resulta invisible e inaccesible, aunque exista en el
     * arbol de vistas con tamaño normal.
     *
     * <p>La correccion mide la altura REAL de la barra tras su propio
     * layout (por eso el {@code post()}: en el momento de
     * {@code onViewCreated} la Activity contenedora todavia no ha
     * terminado de medirse) y fija el margen del FAB a esa altura mas el
     * espaciado habitual, en vez de asumir un valor de un dispositivo de
     * referencia.</p>
     */
    private void ajustarMargenSobreBarraInferior(@NonNull View fab) {
        View bottomNav = requireActivity().findViewById(R.id.bottomNav);
        if (bottomNav == null) {
            return;
        }
        bottomNav.post(() -> {
            if (getView() == null) {
                // El fragmento pudo destruirse antes de que se ejecute
                // este callback diferido.
                return;
            }
            int alturaBarra = bottomNav.getHeight();
            if (alturaBarra <= 0) {
                return;
            }
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) fab.getLayoutParams();
            params.bottomMargin = alturaBarra + getResources().getDimensionPixelSize(R.dimen.space_m);
            fab.setLayoutParams(params);
        });
    }

    private boolean onOpcionDeMenu(@NonNull android.view.MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_add_manual) {
            startActivity(new Intent(requireContext(), CreateProductActivity.class));
            return true;
        }
        if (id == R.id.action_stats) {
            startActivity(new Intent(requireContext(), StatsActivity.class));
            return true;
        }
        if (id == R.id.action_logout) {
            cerrarSesion();
            return true;
        }
        return false;
    }

    private void cerrarSesion() {
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Se recarga al volver de "alta manual" o del escaneo, donde el
        // inventario puede haber cambiado.
        cargarInventario();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (llamadaEnCurso != null) {
            llamadaEnCurso.cancel();
            llamadaEnCurso = null;
        }
        // Evita que el adaptador retenga las vistas del fragmento
        // destruido (fuga de memoria clasica con RecyclerView).
        inventarioList.setAdapter(null);
    }

    private void cargarInventario() {
        swipeRefresh.setRefreshing(true);
        llamadaEnCurso = RetrofitClient.getApi().listarInventario();
        llamadaEnCurso.enqueue(new Callback<List<InventarioDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<InventarioDto>> call,
                                   @NonNull Response<List<InventarioDto>> response) {
                if (getView() == null) {
                    return;
                }
                swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    mostrarInventario(response.body());
                } else {
                    Log.w(TAG, "Respuesta no exitosa al listar inventario: HTTP " + response.code());
                    mostrarError();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<InventarioDto>> call, @NonNull Throwable t) {
                if (call.isCanceled() || getView() == null) {
                    return;
                }
                swipeRefresh.setRefreshing(false);
                Log.e(TAG, "Fallo de red al cargar el inventario", t);
                mostrarError();
            }
        });
    }

    private void mostrarInventario(List<InventarioDto> inventario) {
        errorState.setVisibility(View.GONE);
        adapter.submitList(inventario);
        countChip.setText(getString(R.string.dashboard_count, inventario.size()));
        boolean vacio = inventario.isEmpty();
        emptyState.setVisibility(vacio ? View.VISIBLE : View.GONE);
        inventarioList.setVisibility(vacio ? View.GONE : View.VISIBLE);
    }

    private void mostrarError() {
        emptyState.setVisibility(View.GONE);
        inventarioList.setVisibility(View.GONE);
        errorState.setVisibility(View.VISIBLE);
    }
}
