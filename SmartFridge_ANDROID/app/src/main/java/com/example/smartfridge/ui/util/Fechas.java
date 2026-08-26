package com.example.smartfridge.ui.util;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.smartfridge.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Utilidades de fecha para la capa de presentacion.
 *
 * <p>El backend serializa {@code LocalDateTime} como ISO-8601
 * ({@code 2026-08-26T10:15:30}), a veces con fraccion de segundo. El
 * codigo heredado parseaba con el patron
 * {@code "MMM d, yyyy, h:mm:ss a"} y {@code Locale.ENGLISH}, que ya no
 * corresponde a lo que envia el servidor Spring Boot: cualquier fecha
 * lanzaba {@link ParseException} y se perdia en un
 * {@code printStackTrace()} silencioso.</p>
 *
 * <p>Se usa {@link SimpleDateFormat} y no {@code java.time} porque
 * {@code minSdk = 24} y {@code java.time} solo esta disponible a
 * partir de API 26 sin activar el <i>desugaring</i> de la biblioteca
 * estandar. Migrar a {@code java.time} + desugaring es una mejora
 * razonable si en el futuro se sube el minSdk o se habilita
 * {@code coreLibraryDesugaringEnabled}.</p>
 */
public final class Fechas {

    private static final String PATRON_ISO = "yyyy-MM-dd'T'HH:mm:ss";

    private Fechas() {
        // Clase de utilidad
    }

    /**
     * Parsea una marca ISO-8601 del backend. Trunca a los 19 primeros
     * caracteres para tolerar tanto {@code ...:30} como
     * {@code ...:30.123456}, evitando tener que mantener dos patrones.
     *
     * @return la fecha, o {@code null} si el texto no es interpretable
     */
    @Nullable
    public static Date parseIso(@Nullable String iso) {
        if (iso == null || iso.length() < 19) {
            return null;
        }
        try {
            // SimpleDateFormat NO es thread-safe: se crea una instancia
            // por llamada en lugar de compartir un campo estatico.
            return new SimpleDateFormat(PATRON_ISO, Locale.US).parse(iso.substring(0, 19));
        } catch (ParseException e) {
            return null;
        }
    }

    /** Fecha corta y legible, p. ej. "26 ago 2026". */
    public static String fechaCorta(@Nullable Date fecha) {
        if (fecha == null) {
            return "--";
        }
        return new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(fecha);
    }

    /** Fecha y hora, p. ej. "26 ago, 14:32". */
    public static String fechaHora(@Nullable String iso) {
        Date fecha = parseIso(iso);
        if (fecha == null) {
            return "--";
        }
        return new SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(fecha);
    }

    /**
     * Dias completos entre hoy y la fecha dada. Ambas se normalizan a
     * medianoche primero: sin eso, "manana a las 08:00" a las 23:00 de
     * hoy daria 0 dias en vez de 1.
     */
    public static long diasHasta(Date objetivo) {
        Calendar hoy = Calendar.getInstance();
        aMedianoche(hoy);
        Calendar otro = Calendar.getInstance();
        otro.setTime(objetivo);
        aMedianoche(otro);
        return TimeUnit.MILLISECONDS.toDays(otro.getTimeInMillis() - hoy.getTimeInMillis());
    }

    private static void aMedianoche(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    /**
     * Texto de caducidad orientado a la accion: al usuario le importa
     * "cuanto me queda", no la fecha absoluta. Solo cuando el plazo es
     * largo (> 7 dias) se muestra la fecha, que entonces si es mas
     * informativa que "caduca en 34 dias".
     */
    public static String textoCaducidad(Context ctx, @Nullable String iso) {
        Date fecha = parseIso(iso);
        if (fecha == null) {
            return ctx.getString(R.string.item_expiry_unknown);
        }
        long dias = diasHasta(fecha);
        if (dias < 0) {
            return ctx.getString(R.string.item_expired, (int) -dias);
        }
        if (dias == 0) {
            return ctx.getString(R.string.item_expires_today);
        }
        if (dias <= 7) {
            return ctx.getString(R.string.item_expires_in, (int) dias);
        }
        return ctx.getString(R.string.item_expires_on, fechaCorta(fecha));
    }

    /**
     * Clasificacion de urgencia usada para tenir la pildora de
     * caducidad. Se devuelve un entero simbolico en lugar del color
     * para que la utilidad no dependa del tema.
     */
    public static int urgencia(@Nullable String iso) {
        Date fecha = parseIso(iso);
        if (fecha == null) {
            return URGENCIA_NEUTRA;
        }
        long dias = diasHasta(fecha);
        if (dias < 0) {
            return URGENCIA_CADUCADO;
        }
        return dias <= 2 ? URGENCIA_PROXIMA : URGENCIA_NEUTRA;
    }

    public static final int URGENCIA_NEUTRA = 0;
    public static final int URGENCIA_PROXIMA = 1;
    public static final int URGENCIA_CADUCADO = 2;
}
