package com.example.smartfridge.ui.asistente;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.smartfridge.R;
import com.example.smartfridge.api.RetrofitClient;
import com.example.smartfridge.api.dto.ChatRequestDto;
import com.example.smartfridge.api.dto.ChatResponseDto;
import com.example.smartfridge.api.dto.ChatTurnoDto;
import com.example.smartfridge.ui.util.Imagenes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Estado y lógica de la conversación con el asistente.
 *
 * <h2>Por qué un ViewModel</h2>
 * Una consulta al modelo tarda 15-30 segundos. Con el estado dentro del
 * Fragment, girar el móvil a mitad destruía la conversación y dejaba la
 * petición huérfana. El ViewModel sobrevive al cambio de configuración.
 *
 * <h2>Un único estado observable</h2>
 * Se expone UNA lista con todo —mensajes, indicador de escritura y
 * errores— en vez de varios LiveData sueltos. Con estados separados es
 * fácil que la interfaz muestre una combinación imposible, porque cada
 * observador se actualiza por su cuenta.
 *
 * <h2>Añadidos de la Fase 12</h2>
 * <ul>
 *   <li><b>Adjunto de imagen.</b> El reescalado y la codificación en
 *       Base64 corren en un executor propio: hacerlo en el hilo de UI
 *       congelaría la pantalla varios cientos de milisegundos justo
 *       después de un toque, que es cuando más se nota.</li>
 *   <li><b>Cola de locución.</b> El id del último mensaje entregado a la
 *       voz vive AQUÍ y no en el Fragment. Si viviera en la vista, cada
 *       rotación volvería a leer en voz alta la última respuesta — un
 *       fallo pequeño en apariencia y muy molesto para quien depende de
 *       la voz.</li>
 *   <li><b>Preferencia de voz persistida</b>, para que quien la necesita
 *       no tenga que activarla en cada arranque.</li>
 * </ul>
 */
public class AsistenteViewModel extends AndroidViewModel {

    private static final String TAG = "AsistenteViewModel";
    private static final String PREFS = "smartfridge_accesibilidad";
    private static final String CLAVE_VOZ = "voz_activa";

    /**
     * Cuántos turnos se reenvían como historial. El backend recorta
     * igualmente por su cuenta; acotar también aquí evita mandar por la
     * red algo que el servidor va a descartar.
     */
    private static final int MAX_TURNOS_HISTORIAL = 10;

    /** Una foto ya preparada para enviar, con su miniatura para la vista previa. */
    public static final class Adjunto {
        public final String base64;
        public final Bitmap miniatura;

        Adjunto(String base64, Bitmap miniatura) {
            this.base64 = base64;
            this.miniatura = miniatura;
        }
    }

    private final List<ChatMessage> conversacion = new ArrayList<>();
    private final MutableLiveData<List<ChatMessage>> mensajes = new MutableLiveData<>(List.of());
    private final MutableLiveData<Boolean> esperandoRespuesta = new MutableLiveData<>(false);
    private final MutableLiveData<Adjunto> adjunto = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> vozActiva = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> aviso = new MutableLiveData<>(null);

    private final ExecutorService trabajos = Executors.newSingleThreadExecutor();
    private final SharedPreferences preferencias;

    @Nullable
    private Call<ChatResponseDto> llamadaEnCurso;
    @Nullable
    private String mensajePendienteDeReintento;
    @Nullable
    private String imagenPendienteDeReintento;

    /** Último mensaje del asistente ya entregado al motor de voz. */
    private long ultimoIdLocutado = 0L;

    /** Se muestra una sola vez la nota sobre convivencia con el lector de pantalla. */
    private boolean notaLectorPantallaMostrada;

    public AsistenteViewModel(@NonNull Application application) {
        super(application);
        this.preferencias = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.vozActiva.setValue(preferencias.getBoolean(CLAVE_VOZ, false));

        // El saludo se genera en local y NO se envía como historial al
        // backend: el modelo nunca lo dijo. El backend, además, descarta
        // los turnos del asistente que encabecen la conversación.
        ChatMessage saludo = ChatMessage.delAsistente(application.getString(R.string.assistant_greeting));
        conversacion.add(saludo);
        // El saludo no se locuta: la pantalla acaba de abrirse y una voz
        // inesperada al entrar es intrusiva, no útil.
        ultimoIdLocutado = saludo.id;
        publicar();
    }

    public LiveData<List<ChatMessage>> mensajes() {
        return mensajes;
    }

    public LiveData<Boolean> esperandoRespuesta() {
        return esperandoRespuesta;
    }

    public LiveData<Adjunto> adjunto() {
        return adjunto;
    }

    public LiveData<Boolean> vozActiva() {
        return vozActiva;
    }

    /** Avisos puntuales para la vista (id de cadena), consumidos una sola vez. */
    public LiveData<Integer> aviso() {
        return aviso;
    }

    public void avisoMostrado() {
        aviso.setValue(null);
    }

    // ------------------------------------------------------------------
    // Envío
    // ------------------------------------------------------------------

    public void enviar(@Nullable String texto) {
        String limpio = texto == null ? "" : texto.trim();
        Adjunto foto = adjunto.getValue();

        if (limpio.isEmpty() && foto == null) {
            return;
        }
        if (Boolean.TRUE.equals(esperandoRespuesta.getValue())) {
            return;
        }
        // El backend exige un mensaje no vacío. Si el usuario solo adjunta
        // una foto y pulsa enviar —el gesto más natural, y el único cómodo
        // para quien tiene dificultades motoras— se pone la pregunta
        // obvia en su lugar en vez de devolverle un error.
        if (limpio.isEmpty()) {
            limpio = getApplication().getString(R.string.assistant_attach_default_question);
        }

        List<ChatTurnoDto> historial = historialParaEnvio();
        conversacion.add(ChatMessage.delUsuario(limpio, foto != null));

        String base64 = foto == null ? null : foto.base64;
        quitarAdjunto();
        consultar(limpio, base64, historial);
    }

    /** Reenvía el último mensaje que falló, con su foto si la llevaba. */
    public void reintentar() {
        if (mensajePendienteDeReintento == null || Boolean.TRUE.equals(esperandoRespuesta.getValue())) {
            return;
        }
        String texto = mensajePendienteDeReintento;
        String imagen = imagenPendienteDeReintento;
        mensajePendienteDeReintento = null;
        imagenPendienteDeReintento = null;
        quitarUltimoSiEsError();
        consultar(texto, imagen, historialParaEnvio());
    }

    private void consultar(String mensaje, @Nullable String imagenBase64, List<ChatTurnoDto> historial) {
        conversacion.add(ChatMessage.escribiendo());
        esperandoRespuesta.setValue(true);
        publicar();

        ChatRequestDto peticion = new ChatRequestDto(
                mensaje, historial, imagenBase64, imagenBase64 == null ? null : Imagenes.MIME);

        llamadaEnCurso = RetrofitClient.getApi().chat(peticion);
        llamadaEnCurso.enqueue(new Callback<ChatResponseDto>() {
            @Override
            public void onResponse(@NonNull Call<ChatResponseDto> call,
                                   @NonNull Response<ChatResponseDto> response) {
                quitarIndicadorDeEscritura();
                esperandoRespuesta.setValue(false);

                ChatResponseDto cuerpo = response.body();
                if (response.isSuccessful() && cuerpo != null && cuerpo.respuesta != null
                        && !cuerpo.respuesta.isBlank()) {
                    if (cuerpo.contexto != null) {
                        Log.d(TAG, "Respuesta generada con " + cuerpo.contexto.unidadesInventario
                                + " unidades en inventario, " + cuerpo.contexto.sensores + " sensores, "
                                + cuerpo.contexto.alertas + " alertas");
                    }
                    conversacion.add(ChatMessage.delAsistente(cuerpo.respuesta));
                    mensajePendienteDeReintento = null;
                    imagenPendienteDeReintento = null;
                } else {
                    Log.w(TAG, "Respuesta no útil del asistente: HTTP " + response.code());
                    mensajePendienteDeReintento = mensaje;
                    imagenPendienteDeReintento = imagenBase64;
                    conversacion.add(ChatMessage.error(
                            getApplication().getString(R.string.assistant_error_server)));
                }
                publicar();
            }

            @Override
            public void onFailure(@NonNull Call<ChatResponseDto> call, @NonNull Throwable t) {
                if (call.isCanceled()) {
                    // Cancelación deliberada al cerrar la pantalla.
                    return;
                }
                Log.e(TAG, "Fallo de red al consultar al asistente", t);
                quitarIndicadorDeEscritura();
                esperandoRespuesta.setValue(false);
                mensajePendienteDeReintento = mensaje;
                imagenPendienteDeReintento = imagenBase64;
                conversacion.add(ChatMessage.error(
                        getApplication().getString(R.string.assistant_error_network)));
                publicar();
            }
        });
    }

    // ------------------------------------------------------------------
    // Imagen adjunta
    // ------------------------------------------------------------------

    /** Prepara una foto elegida en la galería o el selector del sistema. */
    public void adjuntarDesdeUri(@NonNull Uri uri) {
        trabajos.execute(() -> {
            try {
                String base64 = Imagenes.comprimirABase64(getApplication(), uri);
                publicarAdjunto(base64);
            } catch (Exception e) {
                Log.e(TAG, "No se pudo preparar la imagen elegida", e);
                aviso.postValue(R.string.assistant_attach_failed);
            }
        });
    }

    /** Prepara la vista previa devuelta por la aplicación de cámara. */
    public void adjuntarDesdeBitmap(@Nullable Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        trabajos.execute(() -> {
            try {
                publicarAdjunto(Imagenes.comprimirABase64(bitmap));
            } catch (Exception e) {
                Log.e(TAG, "No se pudo preparar la foto capturada", e);
                aviso.postValue(R.string.assistant_attach_failed);
            }
        });
    }

    private void publicarAdjunto(@Nullable String base64) {
        if (base64 == null) {
            aviso.postValue(R.string.assistant_attach_failed);
            return;
        }
        adjunto.postValue(new Adjunto(base64, Imagenes.miniatura(base64)));
        aviso.postValue(R.string.assistant_attach_added);
    }

    public void quitarAdjunto() {
        adjunto.setValue(null);
    }

    // ------------------------------------------------------------------
    // Voz
    // ------------------------------------------------------------------

    public void alternarVoz() {
        boolean nuevo = !Boolean.TRUE.equals(vozActiva.getValue());
        vozActiva.setValue(nuevo);
        preferencias.edit().putBoolean(CLAVE_VOZ, nuevo).apply();
        if (!nuevo) {
            // Al apagar la voz, lo ya dicho queda dicho: se marca todo
            // como locutado para que reactivarla no dispare de golpe los
            // mensajes acumulados mientras estaba apagada.
            marcarTodoComoLocutado();
        }
    }

    /**
     * Devuelve el texto de la última respuesta del asistente que aún no
     * se ha entregado a la voz, y la marca como entregada.
     *
     * <p>El estado vive en el ViewModel a propósito: si lo guardara el
     * Fragment, cada rotación de pantalla volvería a locutar la última
     * respuesta.</p>
     *
     * @return el Markdown a locutar, o {@code null} si no hay nada nuevo
     */
    @Nullable
    public String consumirRespuestaParaVoz() {
        for (int i = conversacion.size() - 1; i >= 0; i--) {
            ChatMessage mensaje = conversacion.get(i);
            if (mensaje.tipo != ChatMessage.Tipo.ASISTENTE) {
                continue;
            }
            if (mensaje.id <= ultimoIdLocutado) {
                return null;
            }
            ultimoIdLocutado = mensaje.id;
            return mensaje.texto;
        }
        return null;
    }

    private void marcarTodoComoLocutado() {
        for (ChatMessage mensaje : conversacion) {
            if (mensaje.tipo == ChatMessage.Tipo.ASISTENTE && mensaje.id > ultimoIdLocutado) {
                ultimoIdLocutado = mensaje.id;
            }
        }
    }

    /** {@code true} la primera vez, para explicar por qué la voz viene apagada. */
    public boolean debeExplicarConvivenciaConLectorDePantalla() {
        if (notaLectorPantallaMostrada
                || Boolean.TRUE.equals(vozActiva.getValue())
                || !LectorDeVoz.hayLectorDePantalla(getApplication())) {
            return false;
        }
        notaLectorPantallaMostrada = true;
        return true;
    }

    // ------------------------------------------------------------------
    // Estado interno
    // ------------------------------------------------------------------

    private List<ChatTurnoDto> historialParaEnvio() {
        List<ChatTurnoDto> turnos = new ArrayList<>();
        for (ChatMessage mensaje : conversacion) {
            if (!mensaje.esTurnoDeConversacion()) {
                continue;
            }
            turnos.add(new ChatTurnoDto(
                    mensaje.tipo == ChatMessage.Tipo.USUARIO
                            ? ChatTurnoDto.ROL_USUARIO
                            : ChatTurnoDto.ROL_ASISTENTE,
                    mensaje.texto));
        }
        int desde = Math.max(0, turnos.size() - MAX_TURNOS_HISTORIAL);
        return new ArrayList<>(turnos.subList(desde, turnos.size()));
    }

    private void quitarIndicadorDeEscritura() {
        for (int i = conversacion.size() - 1; i >= 0; i--) {
            if (conversacion.get(i).tipo == ChatMessage.Tipo.ESCRIBIENDO) {
                conversacion.remove(i);
                return;
            }
        }
    }

    private void quitarUltimoSiEsError() {
        if (!conversacion.isEmpty()
                && conversacion.get(conversacion.size() - 1).tipo == ChatMessage.Tipo.ERROR) {
            conversacion.remove(conversacion.size() - 1);
        }
    }

    /**
     * Publica una COPIA inmutable. Si se emitiera la lista interna,
     * ListAdapter recibiría siempre la misma instancia y DiffUtil la
     * compararía consigo misma, sin detectar ningún cambio.
     */
    private void publicar() {
        mensajes.setValue(Collections.unmodifiableList(new ArrayList<>(conversacion)));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (llamadaEnCurso != null) {
            llamadaEnCurso.cancel();
            llamadaEnCurso = null;
        }
        // Sin este shutdown, el hilo del executor mantendría vivo el
        // proceso y una referencia a la Application tras cerrar la
        // pantalla.
        trabajos.shutdownNow();
    }
}
