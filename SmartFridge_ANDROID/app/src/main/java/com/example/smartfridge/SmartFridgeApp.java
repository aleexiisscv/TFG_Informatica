package com.example.smartfridge;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

/**
 * Punto de entrada del proceso.
 *
 * <p>Su unica responsabilidad es activar el <b>color dinamico</b> de
 * Material You. {@code applyToActivitiesIfAvailable} registra un
 * {@code ActivityLifecycleCallbacks} que, en Android 12 (API 31) o
 * superior, envuelve el tema de cada Activity con la paleta derivada
 * del fondo de pantalla del usuario ANTES de que se infle su primera
 * vista.</p>
 *
 * <p><b>Por que asi y no de otra forma</b> (justificacion para la
 * memoria): la alternativa era llamar a
 * {@code DynamicColors.applyToActivityIfAvailable(this)} en el
 * {@code onCreate} de cada Activity. Eso obliga a recordar hacerlo en
 * cada pantalla nueva —un fallo por omision silencioso, que solo se
 * nota como una pantalla "descolorida" en mitad de la app— y viola el
 * principio de responsabilidad unica: cada Activity acabaria sabiendo
 * de temas ademas de su propia logica. Centralizarlo aqui lo convierte
 * en una decision de la aplicacion, no de la pantalla.</p>
 *
 * <p>En dispositivos con Android 11 o anterior la llamada no hace nada
 * y el tema conserva la paleta de respaldo definida en
 * {@code values/colors.xml} + {@code values/themes.xml}. Es decir: el
 * color dinamico es una MEJORA progresiva, nunca un requisito.</p>
 */
public class SmartFridgeApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
