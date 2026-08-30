package com.example.smartfridge.ui.dashboard;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartfridge.R;
import com.example.smartfridge.api.dto.InventarioDto;
import com.example.smartfridge.ui.util.Fechas;
import com.example.smartfridge.ui.util.NutriScoreUi;

import java.util.Objects;

/**
 * Adaptador del inventario.
 *
 * <p>Reemplaza al bucle que {@code DashboardActivity} ejecutaba sobre
 * un {@code TableLayout}, creando a mano un {@code TableRow} y tres
 * {@code TextView} por producto. Aquel enfoque tenia tres defectos
 * estructurales:</p>
 * <ol>
 *   <li><b>Sin reciclado.</b> Con N productos se instanciaban 4N vistas
 *       y todas permanecian en memoria. RecyclerView mantiene solo las
 *       visibles mas un pequeño margen.</li>
 *   <li><b>Redibujado total.</b> Cada refresco hacia
 *       {@code removeAllViews()} y reconstruia la tabla entera, lo que
 *       ademas perdia la posicion de scroll.</li>
 *   <li><b>Presentacion mezclada con datos.</b> El formateo vivia en la
 *       Activity.</li>
 * </ol>
 *
 * <p>Se extiende {@link ListAdapter} en lugar de
 * {@code RecyclerView.Adapter} para aprovechar {@link DiffUtil}: al
 * recibir la nueva lista, la biblioteca calcula en segundo plano que
 * filas cambiaron y anima solo esas. Es la diferencia entre "toda la
 * lista parpadea cada 2 s" y "la fila que caduca cambia de color".</p>
 */
public class InventarioAdapter extends ListAdapter<InventarioDto, InventarioAdapter.UnidadViewHolder> {

    public InventarioAdapter() {
        super(DIFF);
    }

    /**
     * Criterio de comparacion. {@code areItemsTheSame} usa el id de la
     * unidad de inventario (identidad); {@code areContentsTheSame}
     * compara los campos que se pintan (contenido). Separar ambos es lo
     * que permite a RecyclerView distinguir "esta fila se ha movido" de
     * "esta fila ha cambiado".
     */
    private static final DiffUtil.ItemCallback<InventarioDto> DIFF =
            new DiffUtil.ItemCallback<InventarioDto>() {
                @Override
                public boolean areItemsTheSame(@NonNull InventarioDto a, @NonNull InventarioDto b) {
                    return Objects.equals(a.id, b.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull InventarioDto a, @NonNull InventarioDto b) {
                    return Objects.equals(a.nombreProducto, b.nombreProducto)
                            && Objects.equals(a.nutriScore, b.nutriScore)
                            && Objects.equals(a.fechaCaducidad, b.fechaCaducidad);
                }
            };

    @NonNull
    @Override
    public UnidadViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_inventario, parent, false);
        return new UnidadViewHolder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull UnidadViewHolder holder, int position) {
        holder.enlazar(getItem(position));
    }

    static class UnidadViewHolder extends RecyclerView.ViewHolder {

        private final TextView scoreBadge;
        private final TextView productName;
        private final TextView expiryChip;
        private final LinearLayout aiInsightRow;
        private final TextView aiInsightText;

        UnidadViewHolder(@NonNull View itemView) {
            super(itemView);
            scoreBadge = itemView.findViewById(R.id.scoreBadge);
            productName = itemView.findViewById(R.id.productName);
            expiryChip = itemView.findViewById(R.id.expiryChip);
            aiInsightRow = itemView.findViewById(R.id.aiInsightRow);
            aiInsightText = itemView.findViewById(R.id.aiInsightText);
        }

        void enlazar(InventarioDto unidad) {
            android.content.Context ctx = itemView.getContext();

            productName.setText(unidad.nombreProducto);

            scoreBadge.setText(NutriScoreUi.etiqueta(unidad.nutriScore));
            scoreBadge.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, NutriScoreUi.colorFondo(unidad.nutriScore))));
            scoreBadge.setTextColor(
                    ContextCompat.getColor(ctx, NutriScoreUi.colorTexto(unidad.nutriScore)));
            scoreBadge.setContentDescription(
                    NutriScoreUi.normalizar(unidad.nutriScore) == null
                            ? ctx.getString(R.string.item_nutriscore_unknown)
                            : ctx.getString(R.string.item_nutriscore,
                                    NutriScoreUi.etiqueta(unidad.nutriScore)));

            String caducidad = Fechas.textoCaducidad(ctx, unidad.fechaCaducidad);
            expiryChip.setText(caducidad);
            pintarUrgencia(ctx, Fechas.urgencia(unidad.fechaCaducidad));

            // ==== Hueco reservado a la IA ====
            // Cuando exista el modelo predictivo, bastara con exponer el
            // texto inferido en el DTO (o en un DTO paralelo) y llamar a
            // mostrarInsightIa(). La tarjeta ya tiene el espacio hecho:
            // integrar IA no obligara a rediseñar la fila.
            ocultarInsightIa();

            // ==== Accesibilidad (Fase 12) ====
            // El layout marca el contenido como noHideDescendants, asi que
            // la tarjeta entera es UN punto de parada para el lector de
            // pantalla y esta es la frase que se lee. Sin ella, TalkBack
            // se detendria tres veces por producto y leeria "B" suelto,
            // que no significa nada sin saber de que producto habla.
            String score = NutriScoreUi.normalizar(unidad.nutriScore) == null
                    ? ctx.getString(R.string.a11y_nutriscore_sin_dato)
                    : ctx.getString(R.string.item_nutriscore, NutriScoreUi.etiqueta(unidad.nutriScore));
            itemView.setContentDescription(ctx.getString(
                    R.string.a11y_item_inventario, unidad.nombreProducto, score, caducidad));
        }

        private void pintarUrgencia(android.content.Context ctx, int urgencia) {
            int colorFondo;
            int colorTexto;
            switch (urgencia) {
                case Fechas.URGENCIA_CADUCADO:
                    colorFondo = R.color.state_alert_container;
                    colorTexto = R.color.state_alert;
                    break;
                case Fechas.URGENCIA_PROXIMA:
                    colorFondo = R.color.state_warn_container;
                    colorTexto = R.color.state_warn;
                    break;
                default:
                    // Estado neutro: se delega en el tema para que siga
                    // al color dinamico.
                    expiryChip.setBackgroundTintList(ColorStateList.valueOf(
                            obtenerColorDeTema(ctx, com.google.android.material.R.attr.colorSurfaceContainerHighest)));
                    expiryChip.setTextColor(
                            obtenerColorDeTema(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant));
                    return;
            }
            expiryChip.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, colorFondo)));
            expiryChip.setTextColor(ContextCompat.getColor(ctx, colorTexto));
        }

        private static int obtenerColorDeTema(android.content.Context ctx, int atributo) {
            android.util.TypedValue valor = new android.util.TypedValue();
            ctx.getTheme().resolveAttribute(atributo, valor, true);
            return valor.data;
        }

        /**
         * Punto de extension para la fase de IA.
         *
         * <p>Anade la sugerencia a la descripcion hablada ademas de
         * pintarla: el contenido de la fila esta oculto al lector de
         * pantalla (noHideDescendants), asi que un texto que solo se
         * escribiera en el TextView seria invisible para quien no ve.</p>
         */
        void mostrarInsightIa(String texto) {
            aiInsightText.setText(texto);
            aiInsightRow.setVisibility(View.VISIBLE);
            CharSequence actual = itemView.getContentDescription();
            itemView.setContentDescription(actual + ". "
                    + itemView.getContext().getString(R.string.a11y_insight_ia, texto));
        }

        private void ocultarInsightIa() {
            aiInsightRow.setVisibility(View.GONE);
        }
    }
}
