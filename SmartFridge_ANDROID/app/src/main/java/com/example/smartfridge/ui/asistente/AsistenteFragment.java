package com.example.smartfridge.ui.asistente;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.example.smartfridge.LoginActivity;
import com.example.smartfridge.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.noties.markwon.Markwon;

/**
 * Pantalla del asistente. Vista pasiva: observa el estado de
 * {@link AsistenteViewModel} y lo dibuja.
 *
 * <h2>Las tres vías de entrada y salida (Fase 12)</h2>
 * <ul>
 *   <li><b>Escribir</b> — el teclado de siempre.</li>
 *   <li><b>Dictar</b> — {@link RecognizerIntent}. Graba la aplicación de
 *       reconocimiento del sistema, así que esta app <b>no necesita el
 *       permiso RECORD_AUDIO</b>.</li>
 *   <li><b>Fotografiar</b> — {@code TakePicturePreview} o el selector de
 *       fotos del sistema ({@code PickVisualMedia}). Ninguno de los dos
 *       exige permiso: la cámara la abre otra aplicación y el selector
 *       entrega solo la foto elegida, sin acceso al resto del
 *       almacenamiento.</li>
 * </ul>
 * Es decir: tres modos de interacción y <b>cero permisos peligrosos</b>.
 * No es solo higiene de seguridad — cada diálogo de permiso es una
 * barrera más para el usuario al que esta fase quiere servir.
 *
 * <h2>Salida por voz</h2>
 * Si el usuario activa la lectura en voz alta, {@link LectorDeVoz} locuta
 * cada respuesta nueva. Si en cambio hay un lector de pantalla activo, la
 * app cede el turno y usa {@code announceForAccessibility()}: TalkBack ya
 * lee la respuesta con la voz y la velocidad que esa persona configuró, y
 * superponer un segundo sintetizador sería contraproducente.
 */
public class AsistenteFragment extends Fragment {

    private static final String TAG = "AsistenteFragment";
    private static final String ASSET_AVATAR = "assistant_avatar.json";

    private AsistenteViewModel viewModel;
    private ChatAdapter chatAdapter;
    private Markwon markwon;
    @Nullable
    private LectorDeVoz lector;
    @Nullable
    private MenuItem itemVoz;

    private RecyclerView chatList;
    private EditText messageInput;
    private TextView assistantStatus;
    private ImageButton sendButton;
    private ImageButton attachButton;
    private FloatingActionButton micButton;
    private View attachmentPreview;
    private ImageView attachmentThumb;
    private ImageButton removeAttachment;
    private ImageView avatarFallback;
    private LottieAnimationView avatarLottie;

    private ActivityResultLauncher<Intent> lanzadorDictado;
    private ActivityResultLauncher<PickVisualMediaRequest> lanzadorGaleria;
    private ActivityResultLauncher<Void> lanzadorCamara;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        lanzadorGaleria = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        viewModel.adjuntarDesdeUri(uri);
                    }
                });

        lanzadorCamara = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bitmap -> viewModel.adjuntarDesdeBitmap(bitmap));
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
        attachButton = view.findViewById(R.id.attachButton);
        micButton = view.findViewById(R.id.micButton);
        attachmentPreview = view.findViewById(R.id.attachmentPreview);
        attachmentThumb = view.findViewById(R.id.attachmentThumb);
        removeAttachment = view.findViewById(R.id.removeAttachment);
        avatarFallback = view.findViewById(R.id.avatarFallback);
        avatarLottie = view.findViewById(R.id.avatarLottie);

        // Markwon se construye una sola vez por vista y se comparte entre
        // el adaptador (que lo renderiza) y el lector de voz (que lo usa
        // para convertir el Markdown a la frase que se locuta). Así la
        // pantalla y la voz nunca dicen cosas distintas.
        markwon = Markwon.create(requireContext());
        chatAdapter = new ChatAdapter(markwon, () -> viewModel.reintentar());

        LinearLayoutManager gestor = new LinearLayoutManager(requireContext());
        gestor.setStackFromEnd(true);
        chatList.setLayoutManager(gestor);
        chatList.setAdapter(chatAdapter);

        lector = new LectorDeVoz(requireContext(), disponible -> {
            if (!disponible) {
                avisar(R.string.assistant_tts_unavailable);
            }
        });

        sendButton.setOnClickListener(v -> enviarMensaje());
        micButton.setOnClickListener(v -> iniciarDictado());
        attachButton.setOnClickListener(v -> preguntarOrigenDeLaFoto());
        removeAttachment.setOnClickListener(v -> {
            viewModel.quitarAdjunto();
            // Al retirar la foto el foco se queda en un botón que acaba de
            // desaparecer: se devuelve al campo de texto para que el
            // usuario de teclado o lector de pantalla no se pierda.
            messageInput.requestFocus();
        });

        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                enviarMensaje();
                return true;
            }
            return false;
        });

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        itemVoz = toolbar.getMenu().findItem(R.id.action_voz);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_voz) {
                viewModel.alternarVoz();
                return true;
            }
            return false;
        });

        prepararAvatar();
        observarViewModel();

        if (viewModel.debeExplicarConvivenciaConLectorDePantalla()) {
            avisar(R.string.assistant_tts_screenreader_note);
        }
    }

    private void observarViewModel() {
        // getViewLifecycleOwner() y NO "this": el ciclo de vida de la
        // VISTA del fragmento es más corto que el del fragmento. Observar
        // con "this" dejaría observadores apuntando a vistas destruidas.
        viewModel.mensajes().observe(getViewLifecycleOwner(), this::alActualizarConversacion);

        viewModel.esperandoRespuesta().observe(getViewLifecycleOwner(), esperando -> {
            boolean ocupado = Boolean.TRUE.equals(esperando);
            // Se bloquea el envío mientras hay una consulta en vuelo:
            // permitir una segunda pregunta antes de responder la primera
            // desordenaría los turnos del historial enviado a Gemini.
            sendButton.setEnabled(!ocupado);
            sendButton.setAlpha(ocupado ? 0.4f : 1f);
            micButton.setEnabled(!ocupado);
            attachButton.setEnabled(!ocupado);
            attachButton.setAlpha(ocupado ? 0.4f : 1f);
            if (avatarLottie.getVisibility() == View.VISIBLE) {
                if (ocupado) {
                    avatarLottie.playAnimation();
                } else {
                    avatarLottie.pauseAnimation();
                }
            }
        });

        viewModel.adjunto().observe(getViewLifecycleOwner(), adjunto -> {
            boolean hayFoto = adjunto != null;
            attachmentPreview.setVisibility(hayFoto ? View.VISIBLE : View.GONE);
            if (hayFoto) {
                attachmentThumb.setImageBitmap(adjunto.miniatura);
            } else {
                attachmentThumb.setImageDrawable(null);
            }
        });

        viewModel.vozActiva().observe(getViewLifecycleOwner(), this::pintarEstadoDeVoz);

        viewModel.aviso().observe(getViewLifecycleOwner(), idCadena -> {
            if (idCadena != null) {
                avisar(idCadena);
                viewModel.avisoMostrado();
            }
        });

        viewModel.sesionCaducada().observe(getViewLifecycleOwner(), caducada -> {
            if (!Boolean.TRUE.equals(caducada) || getView() == null) {
                return;
            }
            viewModel.sesionCaducadaAtendida();
            // Snackbar con acción en vez de navegar solos: sacar al
            // usuario de la conversación sin avisar sería desconcertante,
            // y más aún para quien la está siguiendo por voz.
            Snackbar barra = Snackbar.make(getView(), R.string.assistant_error_session,
                    Snackbar.LENGTH_INDEFINITE);
            barra.setAnchorView(micButton);
            barra.setAction(R.string.auth_session_expired_action, v -> volverAlLogin());
            barra.show();
        });
    }

    private void alActualizarConversacion(List<ChatMessage> mensajes) {
        chatAdapter.submitList(mensajes, this::desplazarAlFinal);

        String nueva = viewModel.consumirRespuestaParaVoz();
        if (nueva == null) {
            return;
        }
        if (Boolean.TRUE.equals(viewModel.vozActiva().getValue()) && lector != null) {
            lector.leer(markwon, nueva);
        } else if (LectorDeVoz.hayLectorDePantalla(requireContext())) {
            // Sin esto, quien usa TalkBack tendría que ir a buscar la
            // respuesta explorando la pantalla, sin saber siquiera que ya
            // ha llegado.
            chatList.announceForAccessibility(LectorDeVoz.aTextoPlano(markwon, nueva));
        }
    }

    private void pintarEstadoDeVoz(Boolean activa) {
        boolean encendida = Boolean.TRUE.equals(activa);
        if (itemVoz != null) {
            itemVoz.setChecked(encendida);
            itemVoz.setIcon(encendida ? R.drawable.ic_volume_up_24 : R.drawable.ic_volume_off_24);
            // El título del item es lo que TalkBack lee: se redacta como
            // la ACCIÓN que ocurrirá al pulsarlo, no como el estado
            // actual, que es lo que espera quien navega a ciegas.
            itemVoz.setTitle(encendida
                    ? R.string.assistant_tts_disable
                    : R.string.assistant_tts_enable);
        }
        if (!encendida && lector != null) {
            lector.parar();
        }
        // Confirmación audible del cambio, para no depender de ver el icono.
        if (getView() != null) {
            getView().announceForAccessibility(getString(
                    encendida ? R.string.assistant_tts_on : R.string.assistant_tts_off));
        }
    }

    private void desplazarAlFinal() {
        if (chatAdapter != null && chatAdapter.getItemCount() > 0) {
            chatList.scrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Al salir de la pestaña, la voz calla: seguir locutando una
        // receta mientras el usuario mira el inventario es desconcertante.
        if (lector != null) {
            lector.parar();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (lector != null) {
            lector.liberar();
            lector = null;
        }
        chatList.setAdapter(null);
        chatAdapter = null;
        itemVoz = null;
    }

    // ------------------------------------------------------------------
    // Avatar
    // ------------------------------------------------------------------

    private void prepararAvatar() {
        if (!existeAnimacion()) {
            Log.i(TAG, "assets/" + ASSET_AVATAR + " no encontrado: se usa el avatar vectorial");
            return;
        }
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
        messageInput.setText("");
        viewModel.enviar(texto);
    }

    /**
     * Diálogo de dos opciones en vez de una hoja inferior con iconos.
     *
     * <p>Un {@code MaterialAlertDialogBuilder} con dos entradas de lista
     * da filas altas, texto grande que respeta el ajuste de fuente del
     * sistema y un recorrido de foco trivial para TalkBack. Una hoja
     * inferior con dos iconos sería más vistosa y bastante peor para el
     * usuario al que va dirigida esta pantalla.</p>
     */
    private void preguntarOrigenDeLaFoto() {
        String[] opciones = {
                getString(R.string.assistant_attach_camera),
                getString(R.string.assistant_attach_gallery)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.assistant_attach_source)
                .setItems(opciones, (dialogo, indice) -> {
                    if (indice == 0) {
                        abrirCamara();
                    } else {
                        abrirGaleria();
                    }
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private void abrirCamara() {
        try {
            lanzadorCamara.launch(null);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "No hay aplicación de cámara disponible", e);
            avisar(R.string.scan_no_camera);
        }
    }

    private void abrirGaleria() {
        // PickVisualMedia usa el selector de fotos del sistema cuando
        // existe y cae a ACTION_OPEN_DOCUMENT cuando no. En ningún caso
        // hace falta permiso de almacenamiento: la app recibe únicamente
        // la foto que el usuario ha elegido.
        lanzadorGaleria.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void iniciarDictado() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.assistant_listening));
        try {
            // La voz del asistente calla mientras se dicta: si no, el
            // micrófono captaría su propia locución.
            if (lector != null) {
                lector.parar();
            }
            mostrarEstado(getString(R.string.assistant_listening));
            lanzadorDictado.launch(intent);
        } catch (ActivityNotFoundException e) {
            ocultarEstado();
            Log.w(TAG, "No hay reconocedor de voz instalado", e);
            avisar(R.string.assistant_no_speech);
        }
    }

    private void mostrarEstado(String texto) {
        // La vista es una live region: TalkBack lo anuncia solo al cambiar.
        assistantStatus.setText(texto);
        assistantStatus.setVisibility(View.VISIBLE);
    }

    private void ocultarEstado() {
        // INVISIBLE y no GONE: mantener el hueco evita que la lista de
        // chat dé un salto vertical cada vez que aparece el estado.
        assistantStatus.setVisibility(View.INVISIBLE);
    }

    private void volverAlLogin() {
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    private void avisar(@StringRes int mensaje) {
        if (getView() == null) {
            return;
        }
        Snackbar barra = Snackbar.make(getView(), mensaje, Snackbar.LENGTH_LONG);
        // El Snackbar se ancla sobre la barra de entrada para no tapar el
        // campo de texto ni el micrófono.
        barra.setAnchorView(micButton);
        barra.show();
    }
}
