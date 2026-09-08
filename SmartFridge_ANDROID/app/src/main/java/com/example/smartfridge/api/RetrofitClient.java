package com.example.smartfridge.api;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Punto único de acceso a la API del backend. Sustituye a la
 * combinación ServerConnectionThread + HttpURLConnection + parseo
 * manual de JSONObject/JSONArray del sistema legacy.
 *
 * Patrón Singleton perezoso: Retrofit internamente ya mantiene su
 * propio pool de conexiones (vía OkHttp) y su caché de adaptadores de
 * llamada, así que crear una sola instancia y reutilizarla en toda la
 * app es tanto una optimización de rendimiento como la forma
 * recomendada de usar la librería.
 */
public final class RetrofitClient {

    // TODO: sustituye por la IP de tu backend en la red local (la misma
    // Wi-Fi que el móvil) y el puerto configurado en application.yml
    // (server.port: 8081 en este proyecto). En el emulador de Android
    // Studio, 10.0.2.2 apunta al localhost de tu propio ordenador — NO
    // a la red local — así que en emulador usarías
    // "http://10.0.2.2:8081/" en su lugar.
    private static final String BASE_URL = "http://192.168.0.191:8081/";

    private static Retrofit retrofit;

    private RetrofitClient() {
        // Clase de utilidad: no instanciable
    }

    public static synchronized SmartFridgeApi getApi() {
        if (retrofit == null) {
            // Los 10 s de readTimeout originales estaban pensados para
            // endpoints de datos, que responden en milisegundos. El
            // asistente de la Fase 11 no: el backend recopila el
            // contexto del frigorífico y luego espera a que Gemini
            // genere la respuesta —un modelo "thinking" razonando sobre
            // una receta puede tardar tranquilamente 15-30 s—. Con el
            // valor anterior, OkHttp abortaba la petición antes de que
            // el backend hubiera terminado y el usuario veía siempre un
            // error de red, aunque el servidor estuviera funcionando
            // perfectamente.
            //
            // connectTimeout se mantiene corto a propósito: no poder
            // ABRIR la conexión sigue siendo un fallo inmediato (IP mal
            // configurada, backend apagado) y no debe hacer esperar al
            // usuario un minuto para descubrirlo.
            OkHttpClient httpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    // Tope absoluto de la llamada completa (DNS + conexión
                    // + envío + lectura). Actúa de red de seguridad para
                    // que una petición no pueda quedarse colgada
                    // indefinidamente si el servidor va goteando bytes.
                    .callTimeout(90, TimeUnit.SECONDS)
                    .build();

            httpClient = httpClient.newBuilder()
                    .addInterceptor(RetrofitClient::autorizar)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(httpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(SmartFridgeApi.class);
    }

    /**
     * Adjunta el JWT a cada petición y detecta la sesión caducada.
     *
     * <p><b>Excepción importante:</b> a {@code /api/auth/**} NO se le pone
     * cabecera. Con Spring Security como Resource Server, un
     * {@code Authorization: Bearer} con un token inválido o caducado
     * provoca un 401 <i>antes</i> de llegar al controlador, aunque la
     * ruta sea pública. Es decir: enviar el token viejo al login
     * impediría iniciar sesión de nuevo — exactamente cuando el usuario
     * más lo necesita. Es un fallo sutil y muy desconcertante de
     * depurar.</p>
     *
     * <p>Ante un 401 se borra la sesión local. La pantalla que hizo la
     * petición se encontrará sin token y podrá reaccionar; no se navega
     * desde aquí porque un interceptor de red no tiene —ni debe tener—
     * conocimiento de la interfaz.</p>
     */
    private static Response autorizar(Interceptor.Chain cadena) throws IOException {
        Request original = cadena.request();
        SesionUsuario sesion = SesionUsuario.get();

        boolean esAutenticacion = original.url().encodedPath().contains("/api/auth/");
        String token = (sesion == null || esAutenticacion) ? null : sesion.token();

        Request peticion = token == null
                ? original
                : original.newBuilder().header("Authorization", "Bearer " + token).build();

        Response respuesta = cadena.proceed(peticion);

        if (respuesta.code() == 401 && sesion != null && !esAutenticacion) {
            sesion.cerrar();
        }
        return respuesta;
    }
}
