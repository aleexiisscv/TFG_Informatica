package com.example.smartfridge.api;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
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
    private static final String BASE_URL = "http://192.168.0.192:8081/";

    private static Retrofit retrofit;

    private RetrofitClient() {
        // Clase de utilidad: no instanciable
    }

    public static synchronized SmartFridgeApi getApi() {
        if (retrofit == null) {
            OkHttpClient httpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(httpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(SmartFridgeApi.class);
    }
}
