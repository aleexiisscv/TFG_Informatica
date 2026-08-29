package com.example.smartfridge.ui.asistente;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartfridge.R;
import com.google.android.material.button.MaterialButton;

import io.noties.markwon.Markwon;

/**
 * Adaptador de la conversación, con cuatro tipos de vista: burbuja
 * entrante, burbuja saliente, indicador de escritura y aviso de error.
 *
 * <h2>Por qué cuatro layouts y no uno configurable</h2>
 * RecyclerView recicla por tipo de vista, así que una burbuja entrante
 * solo se reutiliza como entrante. Con un único layout habría que
 * revertir en cada {@code onBind} el fondo, el color del texto, el
 * margen y la gravedad; olvidar una de esas reversiones produce el error
 * clásico de "un mensaje mío aparece alineado a la izquierda al hacer
 * scroll". Con tipos separados, ese error es imposible por construcción.
 *
 * <h2>Renderizado de Markdown</h2>
 * El backend pide a Gemini que responda en Markdown porque el contenido
 * habitual son recetas: pasos numerados, ingredientes en lista y
 * negritas para lo importante. Sin renderizar, el usuario vería los
 * asteriscos en crudo. Se usa Markwon, que convierte el Markdown a
 * {@code Spanned} nativo y lo aplica al TextView — sin WebView, sin
 * pérdida de accesibilidad y respetando el color y la tipografía del
 * tema (y por tanto el color dinámico de Material You).
 *
 * <p>La instancia de {@link Markwon} se crea UNA vez y se reutiliza: su
 * construcción compila el conjunto de plugins y no es gratis, así que
 * hacerla dentro de {@code onBindViewHolder} penalizaría cada scroll.</p>
 */
public class ChatAdapter extends ListAdapter<ChatMessage, RecyclerView.ViewHolder> {

    private static final int TIPO_ENTRANTE = 0;
    private static final int TIPO_SALIENTE = 1;
    private static final int TIPO_ESCRIBIENDO = 2;
    private static final int TIPO_ERROR = 3;

    /** Se avisa al Fragment/ViewModel; el adaptador no conoce la red. */
    public interface OnReintentarListener {
        void onReintentar();
    }

    private final Markwon markwon;
    private final OnReintentarListener onReintentar;

    public ChatAdapter(@NonNull Markwon markwon, @NonNull OnReintentarListener onReintentar) {
        super(DIFF);
        this.markwon = markwon;
        this.onReintentar = onReintentar;
    }

    private static final DiffUtil.ItemCallback<ChatMessage> DIFF =
            new DiffUtil.ItemCallback<ChatMessage>() {
                @Override
                public boolean areItemsTheSame(@NonNull ChatMessage a, @NonNull ChatMessage b) {
                    return a.id == b.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull ChatMessage a, @NonNull ChatMessage b) {
                    return a.equals(b);
                }
            };

    @Override
    public int getItemViewType(int position) {
        ChatMessage mensaje = getItem(position);
        switch (mensaje.tipo) {
            case USUARIO:
                return TIPO_SALIENTE;
            case ESCRIBIENDO:
                return TIPO_ESCRIBIENDO;
            case ERROR:
                return TIPO_ERROR;
            default:
                return TIPO_ENTRANTE;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TIPO_SALIENTE:
                // El mensaje del usuario NO se pasa por Markwon: es texto
                // que ha escrito una persona, y renderizar su Markdown
                // haría desaparecer un asterisco escrito a propósito.
                return new TextoPlanoViewHolder(inflater.inflate(R.layout.item_chat_out, parent, false));
            case TIPO_ESCRIBIENDO:
                return new EscribiendoViewHolder(inflater.inflate(R.layout.item_chat_typing, parent, false));
            case TIPO_ERROR:
                return new ErrorViewHolder(inflater.inflate(R.layout.item_chat_error, parent, false), onReintentar);
            default:
                return new MarkdownViewHolder(inflater.inflate(R.layout.item_chat_in, parent, false), markwon);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage mensaje = getItem(position);
        if (holder instanceof MarkdownViewHolder) {
            ((MarkdownViewHolder) holder).enlazar(mensaje);
        } else if (holder instanceof TextoPlanoViewHolder) {
            ((TextoPlanoViewHolder) holder).enlazar(mensaje);
        } else if (holder instanceof ErrorViewHolder) {
            ((ErrorViewHolder) holder).enlazar(mensaje);
        }
        // El indicador de escritura no necesita datos: se anima solo.
    }

    /** Burbuja del asistente: el texto llega en Markdown. */
    static class MarkdownViewHolder extends RecyclerView.ViewHolder {

        private final TextView bubbleText;
        private final Markwon markwon;

        MarkdownViewHolder(@NonNull View itemView, @NonNull Markwon markwon) {
            super(itemView);
            this.bubbleText = itemView.findViewById(R.id.bubbleText);
            this.markwon = markwon;
        }

        void enlazar(ChatMessage mensaje) {
            markwon.setMarkdown(bubbleText, mensaje.texto);
        }
    }

    /** Burbuja del usuario: texto tal cual. */
    static class TextoPlanoViewHolder extends RecyclerView.ViewHolder {

        private final TextView bubbleText;

        TextoPlanoViewHolder(@NonNull View itemView) {
            super(itemView);
            this.bubbleText = itemView.findViewById(R.id.bubbleText);
        }

        void enlazar(ChatMessage mensaje) {
            bubbleText.setText(mensaje.texto);
        }
    }

    static class EscribiendoViewHolder extends RecyclerView.ViewHolder {
        EscribiendoViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    static class ErrorViewHolder extends RecyclerView.ViewHolder {

        private final TextView errorText;

        ErrorViewHolder(@NonNull View itemView, @NonNull OnReintentarListener onReintentar) {
            super(itemView);
            this.errorText = itemView.findViewById(R.id.errorText);
            MaterialButton retry = itemView.findViewById(R.id.retryChatButton);
            // El listener se asigna en onCreateViewHolder, no en onBind:
            // la vista se recicla pero el listener no cambia nunca, así
            // que reasignarlo en cada bind sería trabajo desperdiciado.
            retry.setOnClickListener(v -> onReintentar.onReintentar());
        }

        void enlazar(ChatMessage mensaje) {
            errorText.setText(mensaje.texto);
        }
    }
}
