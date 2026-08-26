package com.example.smartfridge;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.smartfridge.api.RetrofitClient;
import com.example.smartfridge.api.dto.RegistroDto;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Contenedor unico de las tres secciones principales.
 *
 * <h3>Por que existe esta clase</h3>
 * En el sistema heredado, Dashboard y Sensores eran Activities
 * independientes que se llamaban entre si con
 * {@code startActivity() + finish()}. Consecuencias medibles: cada
 * salto destruia y reconstruia toda la jerarquia de vistas, cada
 * pantalla tenia que repintar su propia barra de navegacion, y el
 * boton "atras" del sistema devolvia al usuario a pantallas ya
 * cerradas. Con un unico {@link NavHostFragment}:
 * <ul>
 *   <li>las tres secciones comparten Activity y ciclo de vida,</li>
 *   <li>la {@link BottomNavigationView} es persistente y no parpadea,</li>
 *   <li>el back stack lo gestiona el Navigation Component.</li>
 * </ul>
 *
 * <h3>Sondeo de alertas centralizado</h3>
 * La comprobacion periodica de alertas vive AQUI y no en el fragmento
 * de inventario (donde estaba antes). Razon: la alerta es informacion
 * global del frigorifico, no de una pantalla concreta; el usuario debe
 * enterarse aunque este mirando el asistente. El resultado se pinta
 * como <i>badge</i> numerico sobre el icono de "Sensores", que es
 * donde el usuario puede actuar sobre ella.
 *
 * <p>El intervalo pasa de 2 s (valor heredado) a 15 s. 2 segundos
 * suponian 1 800 peticiones HTTP por hora con la app abierta: coste de
 * bateria y de red injustificado para un dato que cambia como mucho
 * cada varios minutos. Ademas, el {@code Handler} se detiene en
 * {@code onPause}, no en {@code onDestroy} como hacia el codigo
 * anterior: asi la app deja de consumir red en cuanto pasa a segundo
 * plano.</p>
 */
public class MainShellActivity extends AppCompatActivity {

    private static final String TAG = "MainShellActivity";
    private static final long INTERVALO_ALERTAS_MS = 15_000L;

    private BottomNavigationView bottomNav;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable tareaAlertas;
    @Nullable
    private Call<List<RegistroDto>> llamadaAlertas;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_shell);

        bottomNav = findViewById(R.id.bottomNav);

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.navHostFragment);
        if (navHost == null) {
            // No deberia ocurrir: el FragmentContainerView lo declara el
            // propio layout. Si ocurre, es un error de programacion y es
            // preferible fallar pronto y ruidosamente.
            throw new IllegalStateException("NavHostFragment no encontrado en activity_main_shell");
        }
        NavController navController = navHost.getNavController();

        // Un unico enlace declarativo: los id del menu coinciden con los
        // id de los destinos del grafo, asi que NavigationUI se encarga
        // de navegar Y de mantener sincronizado el item seleccionado.
        NavigationUI.setupWithNavController(bottomNav, navController);
    }

    @Override
    protected void onResume() {
        super.onResume();
        tareaAlertas = new Runnable() {
            @Override
            public void run() {
                comprobarAlertas();
                handler.postDelayed(this, INTERVALO_ALERTAS_MS);
            }
        };
        handler.post(tareaAlertas);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tareaAlertas != null) {
            handler.removeCallbacks(tareaAlertas);
        }
        if (llamadaAlertas != null) {
            llamadaAlertas.cancel();
            llamadaAlertas = null;
        }
    }

    private void comprobarAlertas() {
        llamadaAlertas = RetrofitClient.getApi().listarRegistros("ALERTA");
        llamadaAlertas.enqueue(new Callback<List<RegistroDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<RegistroDto>> call,
                                   @NonNull Response<List<RegistroDto>> response) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    pintarBadge(response.body().size());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<RegistroDto>> call, @NonNull Throwable t) {
                if (call.isCanceled()) {
                    return;
                }
                // Un fallo de red NO debe tumbar la app ni molestar al
                // usuario con un dialogo: simplemente no se actualiza el
                // indicador en este ciclo.
                Log.w(TAG, "No se pudieron comprobar las alertas", t);
            }
        });
    }

    private void pintarBadge(int numeroDeAlertas) {
        if (numeroDeAlertas <= 0) {
            bottomNav.removeBadge(R.id.sensoresFragment);
            return;
        }
        BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.sensoresFragment);
        badge.setVisible(true);
        badge.setNumber(numeroDeAlertas);
        badge.setContentDescriptionNumberless(getString(R.string.common_alerts_desc));
    }
}
