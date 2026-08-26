package com.example.smartfridge.ui.util;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;

import com.example.smartfridge.R;

/**
 * Traduce la letra de Nutri-Score a sus recursos de color.
 *
 * <p>Se aisla en una clase propia (y no en el adaptador) porque el
 * mismo mapeo se usa en dos pantallas distintas: la tarjeta de
 * inventario y las barras de estadisticas. Duplicar un {@code switch}
 * de colores en dos sitios es la via mas rapida a que uno de los dos
 * se quede desactualizado.</p>
 *
 * <p>Los colores NO son roles del tema: el Nutri-Score es un codigo
 * normativo y debe verse igual en todos los dispositivos, tambien con
 * color dinamico activo.</p>
 */
public final class NutriScoreUi {

    private NutriScoreUi() {
        // Clase de utilidad
    }

    /** Normaliza a una letra A-E, o {@code null} si el dato no es valido. */
    @Nullable
    public static String normalizar(@Nullable String bruto) {
        if (bruto == null) {
            return null;
        }
        String letra = bruto.trim().toUpperCase();
        return letra.matches("[A-E]") ? letra : null;
    }

    @ColorRes
    public static int colorFondo(@Nullable String bruto) {
        String letra = normalizar(bruto);
        if (letra == null) {
            return R.color.nutri_unknown;
        }
        switch (letra) {
            case "A": return R.color.nutri_a;
            case "B": return R.color.nutri_b;
            case "C": return R.color.nutri_c;
            case "D": return R.color.nutri_d;
            default:  return R.color.nutri_e;
        }
    }

    /**
     * El amarillo de la "C" no alcanza contraste suficiente con texto
     * blanco (ratio < 3:1), asi que solo esa categoria usa texto
     * oscuro. Es una excepcion deliberada, no un descuido.
     */
    @ColorRes
    public static int colorTexto(@Nullable String bruto) {
        return "C".equals(normalizar(bruto)) ? R.color.nutri_on_dark : R.color.nutri_on_light;
    }

    /** Etiqueta que se pinta dentro del circulo. */
    public static String etiqueta(@Nullable String bruto) {
        String letra = normalizar(bruto);
        return letra != null ? letra : "?";
    }
}
