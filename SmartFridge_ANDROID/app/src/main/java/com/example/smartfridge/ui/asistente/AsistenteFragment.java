package com.example.smartfridge.ui.asistente;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.example.smartfridge.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.noties.markwon.Markwon;

/**
 * Pantalla del asistente. A partir de la Fase 11 es una vista "tonta":
 * observa el estado de {@link AsistenteViewModel} y lo dibuja.
 *
 * <p>Todo lo que antes vivía aquí —la lista de mensajes, la lógica de
 * respuesta— se ha movido al ViewModel. Lo que queda es lo propio de un
 * Fragment: inflar, enlazar vistas, traducir toques en llamadas y
 * suscribirse a LiveData. Es lo que permite que girar el móvil en mitad
 * de una consulta ya no pierda la conversación.</p>
 *
 * <p>Se conservan de la fase anterior el avatar con degradación elegante
 * (Lottie si el asset existe, vector si no) y el dictado por voz vía
 * {@code RecognizerIntent}, que sigue sin requerir el permiso
 * RECORD_AUDIO porque graba la app de reconocimiento del sistema.</p>
 */
public class AsistenteFragment extends Fragment {

    private static final String TAG = "AsistenteFragment";
    private static final String ASSET_AVATAR = "assistant_avatar.json";

    private AsistenteViewModel viewModel;
    private ChatAdapter chatAdapter;

    private RecyclerView chatList;
    private EditText messageInput;
    private TextView assistantStatus;
    private ImageButton sendButton;
    private FloatingActionButton micButton;
    private ImageView avatarFallback;
    private LottieAnimationView avatarLottie;

    private ActivityResultLauncher<Intent> lanzadorDictado;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // El ViewModel se pide con "this" (el Fragment) como propietario:
        // sobrevive a los cambios de configuración pero muere cuando el
        // fragmento se destruye de verdad.
        viewModel = new ViewModelProvider(this).get(AsistenteViewModel.class);

        // Los launchers deben registrarse antes de que el fragmento
        // alcance el estado STARTED; onCreate es el punto correcto.
        lanzadorDictado = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                resultado -> {
                    ocultarEstado();
                    if (resultado.getResultCode() != android.app.Activity.RESULT_OK
                            || resultado.getData() == null) {
                        return;
                    }
                    List<String> textos = resultado.getData()
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (textos != null && !textos.isEmpty()) {
                        // El reconocedor devuelve las hipótesis ordenadas
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
        sendButton = view.findViewById(R.id.sendButton);
        micButton = view.findViewById(R.id.micButton);
        avatarFallback = view.findViewById(R.id.avatarFallback);
        avatarLottie = view.findViewById(R.id.avatarLottie);

        // Markwon se construye una sola vez por vista y se comparte con
        // el adaptador: compilar sus plugins en cada onBind penalizaría
        // el scroll.
        Markwon markwon = Markwon.create(requireContext());
        chatAdapter = new ChatAdapter(markwon, () -> viewModel.reintentar());

        LinearLayoutManager gestor = new LinearLayoutManager(requireContext());
        // La conversación crece hacia arriba, como en cualquier app de
        // mensajería.
        gestor.setStackFromEnd(true);
        chatList.setLayoutManager(gestor);
        chatList.setAdapter(chatAdapter);

        sendButton.setOnClickListener(v -> enviarMensaje());
        micButton.setOnClickListener(v -> iniciarDictado());

        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                enviarMensaje();
                return true;
            }
            return false;
        });

        prepararAvatar();
        observarViewModel();
    }

    private void observarViewModel() {
        // getViewLifecycleOwner() y NO "this": el ciclo de vida de la
        // VISTA del fragmento es más corto que el del fragmento. Observar
        // con "this" dejaría observadores vivos apuntando a vistas ya
        // destruidas — la fuga de memoria más habitual con LiveData en
        // fragmentos.
        viewModel.mensajes().observe(getViewLifecycleOwner(), mensajes ->
                chatAdapter.submitList(mensajes, this::desplazarAlFinal));

        viewModel.esperandoRespuesta().observe(getViewLifecycleOwner(), esperando -> {
            boolean ocupado = Boolean.TRUE.equals(esperando);
            // Se deshabilita el envío mientras hay una consulta en vuelo:
            // permitir una segunda pregunta antes de responder la primera
            // desordenaría los turnos del historial que se manda a Gemini.
            sendButton.setEnabled(!ocupado);
            sendButton.setAlpha(ocupado ? 0.4f : 1f);
            micButton.setEnabled(!ocupado);
            if (avatarLottie.getVisibility() == View.VISIBLE) {
                // El avatar se anima mientras el asistente "piensa" y se
                // queda quieto el resto del tiempo: refuerza el estado sin
                // ocupar espacio en pantalla.
                if (ocupado) {
                    avatarLottie.playAnimation();
                } else {
                    avatarLottie.pauseAnimation();
                }
            }
        });
    }

    private void desplazarAlFinal() {
        if (chatAdapter.getItemCount() > 0) {
            chatList.scrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // El adaptador retiene las vistas; soltarlo evita que el
        // RecyclerView de una vista destruida siga referenciado.
        chatList.setAdapter(null);
        chatAdapter = null;
    }

    // ------------------------------------------------------------------
    // Avatar
    // ------------------------------------------------------------------

    private void prepararAvatar() {
        if (!existeAnimacion()) {
            Log.i(TAG, "assets/" + ASSET_AVATAR + " no encontrado: se usa el avatar vectorial");
            return;
        }
        // Si el JSON existe pero está corrupto, Lottie avisa por este
        // listener en vez de lanzar: se vuelve al vector.
        avatarLottie.setFailureListener(error -> {
            Log.w(TAG, "La animación Lottie no se pudo cargar", error);
            avatarLottie.setVisibility(View.GONE);
            avatarFallback.setVisibility(View.VISIBLE);
        });
        avatarLottie.setAnimation(ASSET_AVATAR);
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
    // Interacción
    // ------------------------------------------------------------------

    private void enviarMensaje() {
        String texto = messageInput.getText() == null ? "" : messageInput.getText().toString().trim();
        if (texto.isEmpty()) {
            return;
        }
        messageInput.setText("");
        viewModel.enviar(texto);
    }

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
            // Dispositivos sin servicio de reconocimiento (algunas ROM sin
            // Servicios de Google). Se informa y se sigue: el teclado
            // continúa disponible.
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
        // chat dé un salto vertical cada vez que aparece el estado.
        assistantStatus.setVisibility(View.INVISIBLE);
    }
}
