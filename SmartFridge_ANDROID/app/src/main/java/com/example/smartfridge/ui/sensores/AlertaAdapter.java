package com.example.smartfridge.ui.sensores;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartfridge.R;
import com.example.smartfridge.api.dto.RegistroDto;
import com.example.smartfridge.ui.util.Fechas;

import java.util.Objects;

/**
 * Lista de alertas recientes.
 *
 * <p>La traduccion de {@code sensorTipo} a texto e icono se concentra
 * aqui. Recordatorio de contrato: el backend serializa el enum
 * {@code TipoSensor} en MAYUSCULAS ("AGUA", "TEMPERATURA", "HUMEDAD",
 * "PUERTA"); el sistema legacy usaba minusculas. Comparar contra el
 * literal equivocado no da error de compilacion — simplemente cae en el
 * {@code default} y toda alerta se describe como generica. Es el tipo
 * de fallo que solo se detecta mirando la pantalla.</p>
 */
public class AlertaAdapter extends ListAdapter<RegistroDto, AlertaAdapter.AlertaViewHolder> {

    public AlertaAdapter() {
        super(DIFF);
    }

    private static final DiffUtil.ItemCallback<RegistroDto> DIFF =
            new DiffUtil.ItemCallback<RegistroDto>() {
                @Override
                public boolean areItemsTheSame(@NonNull RegistroDto a, @NonNull RegistroDto b) {
                    return Objects.equals(a.id, b.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull RegistroDto a, @NonNull RegistroDto b) {
                    return Objects.equals(a.sensorTipo, b.sensorTipo)
                            && Objects.equals(a.nombreProducto, b.nombreProducto)
                            && Objects.equals(a.medicion, b.medicion)
                            && Objects.equals(a.fecha, b.fecha);
                }
            };

    @NonNull
    @Override
    public AlertaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_alerta, parent, false);
        return new AlertaViewHolder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull AlertaViewHolder holder, int position) {
        holder.enlazar(getItem(position));
    }

    /**
     * Texto de la alerta.
     *
     * <p>Fase 13: hay dos clases de alerta y se distinguen por qué campo
     * viene relleno. Las de <b>sensor</b> traen {@code sensorTipo}; las de
     * <b>caducidad</b>, que genera la tarea diaria del backend, traen
     * {@code nombreProducto} y los días restantes en {@code medicion}.</p>
     *
     * <p>Se reutiliza el tipo ALERTA para las dos —en lugar de crear un
     * TipoRegistro nuevo— precisamente para que aparezcan juntas aquí:
     * al usuario le da igual si el aviso viene de un sensor o de un
     * calendario, lo que quiere es una sola lista de "cosas que mirar".</p>
     */
    static CharSequence textoDe(Context ctx, RegistroDto registro) {
        if (registro.nombreProducto != null && !registro.nombreProducto.isBlank()) {
            int dias = registro.medicion == null ? 0 : Math.round(registro.medicion);
            if (dias < 0) {
                return ctx.getString(R.string.alert_expired, registro.nombreProducto, -dias);
            }
            if (dias == 0) {
                return ctx.getString(R.string.alert_expiry_today, registro.nombreProducto);
            }
            return ctx.getString(R.string.alert_expiry_soon, registro.nombreProducto, dias);
        }
        return ctx.getString(descripcionDe(registro.sensorTipo));
    }

    @StringRes
    static int descripcionDe(@Nullable String sensorTipo) {
        if (sensorTipo == null) {
            return R.string.alert_generic;
        }
        switch (sensorTipo) {
            case "AGUA":        return R.string.alert_water;
            case "TEMPERATURA": return R.string.alert_temperature;
            case "HUMEDAD":     return R.string.alert_humidity;
            case "PUERTA":      return R.string.alert_door;
            default:            return R.string.alert_generic;
        }
    }

    @DrawableRes
    static int iconoDe(RegistroDto registro) {
        // Un reloj para las caducidades: distingue de un vistazo el aviso
        // que depende del tiempo del que depende de una medida física.
        if (registro.nombreProducto != null && !registro.nombreProducto.isBlank()) {
            return R.drawable.ic_schedule_24;
        }
        return iconoDeSensor(registro.sensorTipo);
    }

    @DrawableRes
    private static int iconoDeSensor(@Nullable String sensorTipo) {
        if (sensorTipo == null) {
            return R.drawable.ic_warning_24;
        }
        switch (sensorTipo) {
            case "AGUA":        return R.drawable.ic_water_24;
            case "TEMPERATURA": return R.drawable.ic_thermostat_24;
            case "HUMEDAD":     return R.drawable.ic_humidity_24;
            case "PUERTA":      return R.drawable.ic_door_24;
            default:            return R.drawable.ic_warning_24;
        }
    }

    static class AlertaViewHolder extends RecyclerView.ViewHolder {

        private final ImageView alertIcon;
        private final TextView alertText;
        private final TextView alertTime;

        AlertaViewHolder(@NonNull View itemView) {
            super(itemView);
            alertIcon = itemView.findViewById(R.id.alertIcon);
            alertText = itemView.findViewById(R.id.alertText);
            alertTime = itemView.findViewById(R.id.alertTime);
        }

        void enlazar(RegistroDto registro) {
            Context ctx = itemView.getContext();
            CharSequence texto = textoDe(ctx, registro);
            String cuando = Fechas.fechaHora(registro.fecha);

            alertIcon.setImageResource(iconoDe(registro));
            alertText.setText(texto);
            alertTime.setText(cuando);

            // Accesibilidad: la fila es un unico punto de parada (ver
            // noHideDescendants en item_alerta.xml). Se antepone la
            // palabra "Alerta" porque el hecho de serlo lo transmite hoy
            // el color rojo de la tarjeta, y el color no llega a quien no
            // lo distingue o no ve la pantalla.
            itemView.setContentDescription(ctx.getString(R.string.a11y_alerta, texto, cuando));
        }
    }
}
