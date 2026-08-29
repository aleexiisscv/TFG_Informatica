package com.example.smartfridge.ui.asistente;

import android.app.Application;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Estado y lógica de la conversación con el asistente.
 *
 * <h2>Por qué un ViewModel y no seguir dentro del Fragment</h2>
 * En la fase anterior la conversación vivía en una lista dentro de
 * {@code AsistenteFragment}. Con respuestas instantáneas y locales eso
 * bastaba; con llamadas de red de 15-30 segundos, no:
 * <ul>
 *   <li><b>Rotación de pantalla.</b> Al girar el móvil, Android destruye
 *       y recrea el Fragment: la conversación entera se perdía y la
 *       petición en vuelo quedaba huérfana. El ViewModel sobrevive al
 *       cambio de configuración, así que el usuario vuelve y su
 *       conversación —y la respuesta que estaba llegando— siguen ahí.</li>
 *   <li><b>Cambio de pestaña.</b> El BottomNavigationView destruye la
 *       vista del fragmento al salir de la sección. Con el estado aquí,
 *       ir a "Inventario" y volver no borra el hilo.</li>
 *   <li><b>Separación de responsabilidades.</b> El Fragment queda como
 *       lo que debe ser: dibuja lo que observa y traduce toques en
 *       llamadas. No sabe qué es Retrofit.</li>
 * </ul>
 *
 * <h2>Un único estado observable</h2>
 * Se expone UNA lista con todo (mensajes, indicador de escritura y
 * errores) en vez de varios LiveData sueltos. Con estados separados
 * ("mensajes" + "cargando" + "error") es fácil que la interfaz muestre
 * una combinación imposible —por ejemplo, escribiendo y error a la vez—
 * porque cada observador se actualiza por su cuenta. Con una sola
 * fuente, la vista no puede desincronizarse consigo misma.
 */
public class AsistenteViewModel extends AndroidViewModel {

    private static final String TAG = "AsistenteViewModel";

    /**
     * Cuántos turnos se reenvían como historial. El backend recorta
     * igualmente por su cuenta ({@code max-turnos-historial}); acotar
     * también aquí evita enviar por la red un historial que el servidor
     * va a descartar.
     */
    private static final int MAX_TURNOS_HISTORIAL = 10;

    private final List<ChatMessage> conversacion = new ArrayList<>();
    private final MutableLiveData<List<ChatMessage>> mensajes = new MutableLiveData<>(List.of());
    private final MutableLiveData<Boolean> esperandoRespuesta = new MutableLiveData<>(false);

    @Nullable
    private Call<ChatResponseDto> llamadaEnCurso;

    /** Último mensaje que falló, para poder reintentarlo sin reescribirlo. */
    @Nullable
    private String mensajePendienteDeReintento;

    public AsistenteViewModel(@NonNull Application application) {
        super(application);
        // El saludo se genera en local y NO se envía como historial al
        // backend: el modelo nunca lo dijo. El backend, además, descarta
        // los turnos del asistente que encabecen la conversación, así que
        // el contrato queda protegido por los dos lados.
        conversacion.add(ChatMessage.delAsistente(application.getString(R.string.assistant_greeting)));
        publicar();
    }

    public LiveData<List<ChatMessage>> mensajes() {
        return mensajes;
    }

    /** Para deshabilitar el botón de enviar mientras hay una consulta en vuelo. */
    public LiveData<Boolean> esperandoRespuesta() {
        return esperandoRespuesta;
    }

    // ------------------------------------------------------------------
    // Acciones
    // ------------------------------------------------------------------

    public void enviar(String texto) {
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.isEmpty() || Boolean.TRUE.equals(esperandoRespuesta.getValue())) {
            return;
        }
        // El historial se calcula ANTES de añadir el mensaje nuevo: este
        // viaja en el campo "mensaje" de la petición, no dentro del
        // historial (ver ChatRequestDto).
        List<ChatTurnoDto> historial = historialParaEnvio();

        conversacion.add(ChatMessage.delUsuario(limpio));
        consultar(limpio, historial);
    }

    /** Reenvía el último mensaje que falló, sin que el usuario tenga que reescribirlo. */
    public void reintentar() {
        if (mensajePendienteDeReintento == null || Boolean.TRUE.equals(esperandoRespuesta.getValue())) {
            return;
        }
        String texto = mensajePendienteDeReintento;
        mensajePendienteDeReintento = null;
        // Se retira el aviso de error anterior para no acumular avisos
        // repetidos si el backend sigue caído.
        quitarUltimoSiEsError();
        consultar(texto, historialParaEnvio());
    }

    private void consultar(String mensaje, List<ChatTurnoDto> historial) {
        conversacion.add(ChatMessage.escribiendo());
        esperandoRespuesta.setValue(true);
        publicar();

        llamadaEnCurso = RetrofitClient.getApi().chat(new ChatRequestDto(mensaje, historial));
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
                        // Trazabilidad: deja constancia de sobre cuántos
                        // datos reales se generó la respuesta. Si el
                        // asistente menciona un ingrediente inexistente y
                        // aquí pone 0 unidades, el fallo está en el prompt.
                        Log.d(TAG, "Respuesta generada con " + cuerpo.contexto.unidadesInventario
                                + " unidades en inventario, " + cuerpo.contexto.sensores + " sensores, "
                                + cuerpo.contexto.alertas + " alertas");
                    }
                    conversacion.add(ChatMessage.delAsistente(cuerpo.respuesta));
                    mensajePendienteDeReintento = null;
                } else {
                    // 502 = el backend no pudo hablar con Gemini;
                    // 400 = mensaje vacío o demasiado largo. En ambos
                    // casos el reintento es razonable, así que se ofrece.
                    Log.w(TAG, "Respuesta no útil del asistente: HTTP " + response.code());
                    mensajePendienteDeReintento = mensaje;
                    conversacion.add(ChatMessage.error(
                            getApplication().getString(R.string.assistant_error_server)));
                }
                publicar();
            }

            @Override
            public void onFailure(@NonNull Call<ChatResponseDto> call, @NonNull Throwable t) {
                if (call.isCanceled()) {
                    // Cancelación deliberada al cerrar la pantalla: no es
                    // un error que deba verse en la conversación.
                    return;
                }
                Log.e(TAG, "Fallo de red al consultar al asistente", t);
                quitarIndicadorDeEscritura();
                esperandoRespuesta.setValue(false);
                mensajePendienteDeReintento = mensaje;
                conversacion.add(ChatMessage.error(
                        getApplication().getString(R.string.assistant_error_network)));
                publicar();
            }
        });
    }

    // ------------------------------------------------------------------
    // Estado interno
    // ------------------------------------------------------------------

    /**
     * Traduce la conversación al formato del backend, quedándose solo con
     * turnos reales y con los últimos {@link #MAX_TURNOS_HISTORIAL}.
     */
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

    /**
     * El ViewModel muere cuando el usuario abandona la pantalla de verdad
     * (no al rotar). Cancelar aquí evita que una respuesta tardía intente
     * actualizar un LiveData ya sin observadores y que la petición siga
     * consumiendo red y cuota de Gemini para nada.
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        if (llamadaEnCurso != null) {
            llamadaEnCurso.cancel();
            llamadaEnCurso = null;
        }
    }
}
