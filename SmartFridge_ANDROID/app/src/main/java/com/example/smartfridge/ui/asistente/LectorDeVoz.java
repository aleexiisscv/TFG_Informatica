package com.example.smartfridge.ui.asistente;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import io.noties.markwon.Markwon;

/**
 * Locución de las respuestas del asistente con el motor
 * {@link TextToSpeech} del sistema.
 *
 * <h2>Por qué no basta con pasarle el texto tal cual</h2>
 * El backend pide a Gemini que responda en Markdown, porque el contenido
 * habitual son recetas. Un sintetizador de voz leería literalmente
 * <i>"asterisco asterisco huevos asterisco asterisco"</i>. Antes de
 * locutar se convierte el Markdown a texto plano reutilizando el mismo
 * {@link Markwon} que ya renderiza las burbujas: {@code toMarkdown()}
 * devuelve un {@code Spanned} donde el formato vive en spans y no en
 * caracteres, así que {@code toString()} da exactamente el texto que un
 * humano leería en voz alta. Cero código de parseo propio y cero riesgo
 * de que la voz y la pantalla digan cosas distintas.
 *
 * <h2>Convivencia con TalkBack</h2>
 * {@link #hayLectorDePantalla(Context)} detecta si el usuario ya tiene un
 * lector de pantalla activo. En ese caso el asistente NO debe locutar por
 * su cuenta: TalkBack ya lee la respuesta, con la voz, el idioma y la
 * velocidad que esa persona ha configurado, y superponer un segundo
 * sintetizador produce dos voces solapadas — un fallo de accesibilidad
 * clásico, y especialmente irónico en una función pensada para mejorarla.
 * La app cede el turno y usa {@code announceForAccessibility()} en su
 * lugar.
 */
public class LectorDeVoz {

    private static final String TAG = "LectorDeVoz";
    private static final String ID_LOCUCION = "smartfridge-respuesta";

    /** Avisa de si el motor quedó utilizable, para poder informar al usuario. */
    public interface Listener {
        void onMotorListo(boolean disponible);
    }

    private final TextToSpeech motor;
    private boolean listo;
    @Nullable
    private String pendiente;

    public LectorDeVoz(@NonNull Context contexto, @NonNull Listener listener) {
        // Se usa el contexto de aplicación: el motor vive más que la vista
        // que lo creó y retener una Activity aquí sería una fuga.
        this.motor = new TextToSpeech(contexto.getApplicationContext(), estado -> {
            boolean ok = estado == TextToSpeech.SUCCESS && configurarIdioma();
            this.listo = ok;
            listener.onMotorListo(ok);
            // La inicialización es asíncrona: si el usuario activó la voz
            // y llegó una respuesta antes de que el motor estuviera listo,
            // se locuta ahora en lugar de perderla.
            if (ok && pendiente != null) {
                hablar(pendiente);
                pendiente = null;
            }
        });
    }

    /**
     * Intenta español y, si el dispositivo no lo tiene instalado, cae al
     * idioma por defecto. Es preferible una voz con acento raro a ninguna.
     */
    private boolean configurarIdioma() {
        int resultado = motor.setLanguage(Locale.forLanguageTag("es-ES"));
        if (resultado != TextToSpeech.LANG_MISSING_DATA
                && resultado != TextToSpeech.LANG_NOT_SUPPORTED) {
            return true;
        }
        Log.w(TAG, "Voz en español no disponible; se prueba el idioma por defecto");
        int alternativa = motor.setLanguage(Locale.getDefault());
        return alternativa != TextToSpeech.LANG_MISSING_DATA
                && alternativa != TextToSpeech.LANG_NOT_SUPPORTED;
    }

    /**
     * Locuta una respuesta en Markdown.
     *
     * @param markwon el mismo renderizador que usa el adaptador de chat
     */
    public void leer(@NonNull Markwon markwon, @Nullable String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return;
        }
        String plano = aTextoPlano(markwon, markdown);
        if (!listo) {
            pendiente = plano;
            return;
        }
        hablar(plano);
    }

    private void hablar(String texto) {
        // QUEUE_FLUSH y no QUEUE_ADD: si llega una respuesta nueva
        // mientras se lee la anterior, lo que el usuario quiere oír es la
        // nueva, no esperar a que termine una que ya no le interesa.
        motor.speak(texto, TextToSpeech.QUEUE_FLUSH, null, ID_LOCUCION);
    }

    /** Convierte Markdown a la frase que un humano leería en voz alta. */
    public static String aTextoPlano(@NonNull Markwon markwon, @NonNull String markdown) {
        return markwon.toMarkdown(markdown).toString().trim();
    }

    public void parar() {
        motor.stop();
        pendiente = null;
    }

    /** Debe llamarse al destruir la vista: el motor es un recurso del sistema. */
    public void liberar() {
        motor.stop();
        motor.shutdown();
        listo = false;
    }

    /**
     * {@code true} si hay un lector de pantalla activo (TalkBack u otro).
     *
     * <p>Se comprueban las dos condiciones: que la accesibilidad esté
     * habilitada Y que la exploración táctil lo esté. Solo la primera
     * también da positivo con servicios que no locutan nada —un teclado
     * de accesibilidad, por ejemplo—, y silenciar la voz por ese motivo
     * sería un falso positivo que dejaría al usuario sin la función.</p>
     */
    public static boolean hayLectorDePantalla(@NonNull Context contexto) {
        AccessibilityManager gestor =
                (AccessibilityManager) contexto.getSystemService(Context.ACCESSIBILITY_SERVICE);
        return gestor != null && gestor.isEnabled() && gestor.isTouchExplorationEnabled();
    }
}
