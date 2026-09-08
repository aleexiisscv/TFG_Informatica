package com.example.smartfridge.api;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.smartfridge.api.dto.AuthResponse;

/**
 * Sesión del usuario: guarda el JWT y sabe si sigue siendo válido.
 *
 * <h2>Por qué un singleton inicializado desde Application</h2>
 * {@link RetrofitClient} es estático y no tiene {@code Context}, pero
 * necesita el token para cada petición. La alternativa era pasar el
 * token por parámetro en cada llamada de la API, lo que obligaría a que
 * todas las pantallas supieran de autenticación. Con este singleton, el
 * interceptor lo consulta y ninguna pantalla se entera.
 *
 * <h2>Sobre dónde se guarda</h2>
 * {@code SharedPreferences} en el almacenamiento privado de la app. En
 * un dispositivo sin rootear ninguna otra aplicación puede leerlo.
 * <b>Mejora pendiente</b>: {@code EncryptedSharedPreferences} de
 * androidx.security, que cifra el valor con una clave del Keystore del
 * dispositivo y protege también frente a una extracción física del
 * almacenamiento. Se documenta como deuda consciente y no se implementa
 * aquí para no arrastrar otra dependencia en la última fase.
 */
public final class SesionUsuario {

    private static final String PREFS = "smartfridge_sesion";
    private static final String CLAVE_TOKEN = "token";
    private static final String CLAVE_NOMBRE = "nombre";
    private static final String CLAVE_CORREO = "correo";
    private static final String CLAVE_EXPIRA = "expira_en_millis";

    /**
     * Margen de seguridad: un token al que le quedan diez segundos se
     * considera ya caducado. Evita la carrera de enviar un token que
     * expira mientras la petición viaja por la red.
     */
    private static final long MARGEN_MS = 10_000L;

    @Nullable
    private static SesionUsuario instancia;

    private final SharedPreferences preferencias;

    private SesionUsuario(Context contexto) {
        this.preferencias = contexto.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Se llama una sola vez, desde {@code SmartFridgeApp.onCreate()}. */
    public static synchronized void inicializar(@NonNull Context contexto) {
        if (instancia == null) {
            instancia = new SesionUsuario(contexto);
        }
    }

    /**
     * @return la sesión, o {@code null} si todavía no se inicializó.
     *         Se devuelve nulo en vez de lanzar porque el interceptor de
     *         red puede ejecutarse en escenarios de arranque poco
     *         habituales, y quedarse sin cabecera es preferible a
     *         tumbar la aplicación.
     */
    @Nullable
    public static SesionUsuario get() {
        return instancia;
    }

    public void guardar(@NonNull AuthResponse respuesta) {
        long expiraEn = respuesta.expiraEnSegundos == null
                ? 0L
                : System.currentTimeMillis() + respuesta.expiraEnSegundos * 1000L;

        preferencias.edit()
                .putString(CLAVE_TOKEN, respuesta.token)
                .putString(CLAVE_NOMBRE, respuesta.nombre)
                .putString(CLAVE_CORREO, respuesta.correo)
                .putLong(CLAVE_EXPIRA, expiraEn)
                .apply();
    }

    /** El token en bruto, o {@code null} si no hay o ya caducó. */
    @Nullable
    public String token() {
        return hayTokenValido() ? preferencias.getString(CLAVE_TOKEN, null) : null;
    }

    public boolean hayTokenValido() {
        String token = preferencias.getString(CLAVE_TOKEN, null);
        if (token == null || token.isEmpty()) {
            return false;
        }
        long expira = preferencias.getLong(CLAVE_EXPIRA, 0L);
        // expira == 0 significa que el servidor no informó de la vigencia:
        // se acepta el token y se deja que el 401 decida. Es preferible a
        // descartar una sesión que quizá siga siendo válida.
        return expira == 0L || System.currentTimeMillis() + MARGEN_MS < expira;
    }

    @Nullable
    public String nombre() {
        return preferencias.getString(CLAVE_NOMBRE, null);
    }

    /** Borra la sesión: cierre manual o 401 del servidor. */
    public void cerrar() {
        preferencias.edit().clear().apply();
    }
}
