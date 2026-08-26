package com.example.smartfridge.ui.asistente;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.example.smartfridge.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Asistente virtual.
 *
 * <h3>Alcance de esta fase</h3>
 * La pantalla implementa TODO lo que no depende del modelo: avatar
 * animado, historial de conversacion con burbujas, entrada de texto y
 * dictado por voz. Lo unico pendiente es la respuesta: hoy
 * {@link #responder(String)} devuelve un texto fijo. Cuando exista el
 * endpoint (o el modelo en dispositivo), ese metodo es el <b>unico</b>
 * punto que hay que tocar — el resto de la pantalla no se entera de
 * quien genera la respuesta. Es una aplicacion directa del principio de
 * inversion de dependencias: la interfaz depende de la abstraccion
 * "algo responde", no de una implementacion concreta.
 *
 * <h3>Avatar: degradacion elegante</h3>
 * Se muestra un vector estatico y, solo si la animacion Lottie existe
 * en {@code assets/} y carga sin error, se sustituye por ella. Asi el
 * diseñador puede anadir o cambiar la animacion sin tocar codigo, y la
 * ausencia del fichero no deja un hueco vacio en la pantalla.
 *
 * <h3>Dictado sin permisos</h3>
 * Se usa {@link RecognizerIntent#ACTION_RECOGNIZE_SPEECH} lanzado como
 * Intent, NO la API {@code SpeechRecognizer} en proceso. La diferencia
 * es importante: con el Intent es la aplicacion de reconocimiento del
 * sistema la que graba, por lo que esta app <b>no necesita el permiso
 * RECORD_AUDIO</b>. Principio de minimo privilegio: no se pide un
 * permiso peligroso para una funcion que el sistema ya ofrece.
 */
public class AsistenteFragment extends Fragment {

    private static final String TAG = "AsistenteFragment";
    private static final String ASSET_AVATAR = "assistant_avatar.json";
    private static final long RETARDO_RESPUESTA_MS = 500L;

    private RecyclerView chatList;
    private ChatAdapter chatAdapter;
    private EditText messageInput;
    private TextView assistantStatus;
    private ImageView avatarFallback;
    private LottieAnimationView avatarLottie;

    private final List<ChatMessage> conversacion = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ActivityResultLauncher<Intent> lanzadorDictado;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // El launcher DEBE registrarse antes de que el fragmento llegue
        // a STARTED; hacerlo en onCreate es el punto correcto segun la
        // API de Activity Result.
        lanzadorDictado = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                resultado -> {
                    ocultarEstado();
                    if (resultado.getResultCode() != android.app.Activity.RESULT_OK
                            || resultado.getData() == null) {
                        return;
                    }
                    ArrayList<String> textos = resultado.getData()
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (textos != null && !textos.isEmpty()) {
                        // El reconocedor devuelve las hipotesis ordenadas
                        // por confianza: la primera es la mejor.
                        messageInput.setText(textos.get(0));
                        enviarMensaje();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_asistente, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        chatList = view.findViewById(R.id.chatList);
        messageInput = view.findViewById(R.id.messageInput);
        assistantStatus = view.findViewById(R.id.assistantStatus);
        avatarFallback = view.findViewById(R.id.avatarFallback);
        avatarLottie = view.findViewById(R.id.avatarLottie);

        chatAdapter = new ChatAdapter();
        LinearLayoutManager gestor = new LinearLayoutManager(requireContext());
        // stackFromEnd ancla la lista abajo: la conversacion crece hacia
        // arriba, como en cualquier app de mensajeria.
        gestor.setStackFromEnd(true);
        chatList.setLayoutManager(gestor);
        chatList.setAdapter(chatAdapter);

        view.findViewById(R.id.sendButton).setOnClickListener(v -> enviarMensaje());

        FloatingActionButton micButton = view.findViewById(R.id.micButton);
        micButton.setOnClickListener(v -> iniciarDictado());

        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                enviarMensaje();
                return true;
            }
            return false;
        });

        prepararAvatar();

        if (conversacion.isEmpty()) {
            anadir(ChatMessage.delAsistente(getString(R.string.assistant_greeting)));
        } else {
            chatAdapter.submitList(new ArrayList<>(conversacion));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        chatList.setAdapter(null);
    }

    // ------------------------------------------------------------------
    // Avatar
    // ------------------------------------------------------------------

    private void prepararAvatar() {
        if (!existeAnimacion()) {
            Log.i(TAG, "assets/" + ASSET_AVATAR + " no encontrado: se usa el avatar vectorial");
            return;
        }
        // Si el JSON existe pero esta corrupto, Lottie avisa por este
        // listener en vez de lanzar: se vuelve al vector.
        avatarLottie.setFailureListener(error -> {
            Log.w(TAG, "La animacion Lottie no se pudo cargar", error);
            avatarLottie.setVisibility(View.GONE);
            avatarFallback.setVisibility(View.VISIBLE);
        });
        avatarLottie.setAnimation(ASSET_AVATAR);
        avatarLottie.playAnimation();
        avatarLottie.setVisibility(View.VISIBLE);
        avatarFallback.setVisibility(View.GONE);
    }

    private boolean existeAnimacion() {
        try {
            String[] assets = requireContext().getAssets().list("");
            return assets != null && Arrays.asList(assets).contains(ASSET_AVATAR);
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Conversacion
    // ------------------------------------------------------------------

    private void enviarMensaje() {
        String texto = messageInput.getText() == null ? "" : messageInput.getText().toString().trim();
        if (texto.isEmpty()) {
            return;
        }
        messageInput.setText("");
        anadir(ChatMessage.delUsuario(texto));

        // Pequeño retardo para que la respuesta no aparezca en el mismo
        // fotograma que la pregunta: el usuario necesita percibir que ha
        // habido un turno de conversacion.
        handler.postDelayed(() -> {
            if (getView() == null) {
                return;
            }
            anadir(ChatMessage.delAsistente(responder(texto)));
        }, RETARDO_RESPUESTA_MS);
    }

    /**
     * PUNTO DE INTEGRACION DE LA IA.
     *
     * <p>Sustituir el cuerpo por una llamada asincrona al backend —por
     * ejemplo {@code POST /api/asistente} devolviendo la respuesta
     * generada, con el inventario y las ultimas lecturas como
     * contexto— o por una inferencia en dispositivo. La firma
     * {@code String -> String} se mantiene deliberadamente sencilla;
     * cuando la respuesta sea asincrona bastara con cambiarla por un
     * callback y llamar a {@link #anadir(ChatMessage)} desde el.</p>
     */
    private String responder(String pregunta) {
        return getString(R.string.assistant_not_wired);
    }

    private void anadir(ChatMessage mensaje) {
        conversacion.add(mensaje);
        // submitList exige una lista NUEVA: si se le pasa la misma
        // instancia mutada, DiffUtil compara la lista consigo misma y no
        // detecta ningun cambio.
        chatAdapter.submitList(new ArrayList<>(conversacion),
                () -> chatList.scrollToPosition(chatAdapter.getItemCount() - 1));
    }

    // ------------------------------------------------------------------
    // Dictado por voz
    // ------------------------------------------------------------------

    private void iniciarDictado() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.assistant_listening));
        try {
            mostrarEstado(getString(R.string.assistant_listening));
            lanzadorDictado.launch(intent);
        } catch (ActivityNotFoundException e) {
            // Dispositivos sin servicio de reconocimiento (algunas ROM
            // sin Servicios de Google). Se informa y se sigue: el
            // teclado sigue disponible.
            ocultarEstado();
            Log.w(TAG, "No hay reconocedor de voz instalado", e);
            Snackbar.make(messageInput, R.string.assistant_no_speech, Snackbar.LENGTH_LONG).show();
        }
    }

    private void mostrarEstado(String texto) {
        assistantStatus.setText(texto);
        assistantStatus.setVisibility(View.VISIBLE);
    }

    private void ocultarEstado() {
        // INVISIBLE y no GONE: mantener el hueco evita que la lista de
        // chat de un salto vertical cada vez que aparece el estado.
        assistantStatus.setVisibility(View.INVISIBLE);
    }
}
