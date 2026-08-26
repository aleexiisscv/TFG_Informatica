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

/**
 * Adaptador de la conversacion, con dos tipos de vista.
 *
 * <p>Se usa {@code getItemViewType} + dos layouts distintos en lugar de
 * un unico layout cuya alineacion se cambia en tiempo de ejecucion.
 * Motivo: RecyclerView recicla por tipo de vista, asi que las burbujas
 * entrantes solo se reutilizan como entrantes. Con un unico tipo habria
 * que revertir en cada {@code onBind} el fondo, el color, el margen y
 * la gravedad — y olvidar una de esas cuatro reversiones produce el
 * error visual clasico de "un mensaje mio aparece alineado a la
 * izquierda al hacer scroll".</p>
 */
public class ChatAdapter extends ListAdapter<ChatMessage, ChatAdapter.BurbujaViewHolder> {

    private static final int TIPO_ENTRANTE = 0;
    private static final int TIPO_SALIENTE = 1;

    public ChatAdapter() {
        super(DIFF);
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
        return getItem(position).delUsuario ? TIPO_SALIENTE : TIPO_ENTRANTE;
    }

    @NonNull
    @Override
    public BurbujaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TIPO_SALIENTE ? R.layout.item_chat_out : R.layout.item_chat_in;
        View vista = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new BurbujaViewHolder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull BurbujaViewHolder holder, int position) {
        holder.enlazar(getItem(position));
    }

    static class BurbujaViewHolder extends RecyclerView.ViewHolder {

        private final TextView bubbleText;

        BurbujaViewHolder(@NonNull View itemView) {
            super(itemView);
            bubbleText = itemView.findViewById(R.id.bubbleText);
        }

        void enlazar(ChatMessage mensaje) {
            bubbleText.setText(mensaje.texto);
        }
    }
}
