package com.example.smartfridge.ui.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Prepara una fotografía para viajar dentro de un JSON hacia el
 * backend.
 *
 * <h2>Por qué comprimir en el móvil y no en el servidor</h2>
 * Una foto de un teléfono actual son 4-8 MB. En Base64 crece un 33 %
 * más: entre 5 y 11 MB por pregunta. Enviarla en crudo significaría
 * varios segundos de subida con datos móviles, un JSON que el backend
 * tiene que deserializar entero en memoria, y otro tanto que Gemini
 * tiene que recibir — todo para analizar un producto que se reconoce
 * perfectamente a 1024 px. Reescalar aquí deja la carga en unos 200 KB:
 * un factor de 30, sin pérdida apreciable para el modelo de visión.
 *
 * <h2>Decodificación en dos pasadas</h2>
 * Primero se leen SOLO las dimensiones ({@code inJustDecodeBounds}) para
 * calcular un {@code inSampleSize}, y solo después se decodifica ya
 * reducida. Decodificar la imagen completa para escalarla luego
 * reservaría los 30-90 MB del bitmap a tamaño real y es la causa más
 * común de {@code OutOfMemoryError} al tratar fotos en Android.
 */
public final class Imagenes {

    /** Lado mayor tras el reescalado. Suficiente para reconocer un envase. */
    private static final int LADO_MAXIMO = 1024;

    /** 80 es el punto donde JPEG deja de mejorar de forma perceptible. */
    private static final int CALIDAD_JPEG = 80;

    public static final String MIME = "image/jpeg";

    private Imagenes() {
        // Clase de utilidad
    }

    /**
     * Lee, rota, reescala, comprime y codifica en Base64 la imagen
     * apuntada por {@code uri}.
     *
     * <p>Es una operación de cientos de milisegundos: debe llamarse
     * desde un hilo de fondo, nunca desde el hilo de UI.</p>
     *
     * @return el Base64 sin saltos de línea, o {@code null} si la imagen
     *         no se pudo leer
     */
    @Nullable
    public static String comprimirABase64(@NonNull Context contexto, @NonNull Uri uri) throws IOException {
        BitmapFactory.Options limites = new BitmapFactory.Options();
        limites.inJustDecodeBounds = true;
        try (InputStream entrada = contexto.getContentResolver().openInputStream(uri)) {
            if (entrada == null) {
                return null;
            }
            BitmapFactory.decodeStream(entrada, null, limites);
        }
        if (limites.outWidth <= 0 || limites.outHeight <= 0) {
            return null;
        }

        BitmapFactory.Options opciones = new BitmapFactory.Options();
        opciones.inSampleSize = calcularSubmuestreo(limites.outWidth, limites.outHeight);
        Bitmap bitmap;
        try (InputStream entrada = contexto.getContentResolver().openInputStream(uri)) {
            if (entrada == null) {
                return null;
            }
            bitmap = BitmapFactory.decodeStream(entrada, null, opciones);
        }
        if (bitmap == null) {
            return null;
        }

        // La orientación EXIF importa mucho aquí: casi todos los móviles
        // guardan la foto en horizontal y anotan la rotación en los
        // metadatos. Sin aplicarla, el modelo de visión recibe el
        // producto tumbado, que es justo lo que peor reconoce.
        bitmap = aplicarOrientacion(contexto, uri, bitmap);

        return comprimirABase64(bitmap);
    }

    /** Variante para un bitmap ya en memoria (la vista previa de la cámara). */
    @Nullable
    public static String comprimirABase64(@Nullable Bitmap original) {
        if (original == null) {
            return null;
        }
        Bitmap reducido = reescalar(original);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        reducido.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, salida);
        if (reducido != original) {
            reducido.recycle();
        }
        return Base64.encodeToString(salida.toByteArray(), Base64.NO_WRAP);
    }

    /**
     * Mayor potencia de 2 que deja la imagen por encima del tamaño
     * objetivo. BitmapFactory solo admite potencias de 2, y quedarse por
     * encima permite después un reescalado exacto sin ampliar.
     */
    private static int calcularSubmuestreo(int ancho, int alto) {
        int mayor = Math.max(ancho, alto);
        int muestreo = 1;
        while (mayor / (muestreo * 2) >= LADO_MAXIMO) {
            muestreo *= 2;
        }
        return muestreo;
    }

    private static Bitmap reescalar(Bitmap original) {
        int mayor = Math.max(original.getWidth(), original.getHeight());
        if (mayor <= LADO_MAXIMO) {
            return original;
        }
        float factor = (float) LADO_MAXIMO / mayor;
        int ancho = Math.round(original.getWidth() * factor);
        int alto = Math.round(original.getHeight() * factor);
        return Bitmap.createScaledBitmap(original, ancho, alto, true);
    }

    private static Bitmap aplicarOrientacion(Context contexto, Uri uri, Bitmap bitmap) {
        int orientacion;
        try (InputStream entrada = contexto.getContentResolver().openInputStream(uri)) {
            if (entrada == null) {
                return bitmap;
            }
            orientacion = new ExifInterface(entrada)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException e) {
            // Sin EXIF legible se asume orientación normal: preferible a
            // perder la foto por un metadato ausente.
            return bitmap;
        }

        Matrix matriz = new Matrix();
        switch (orientacion) {
            case ExifInterface.ORIENTATION_ROTATE_90:  matriz.postRotate(90); break;
            case ExifInterface.ORIENTATION_ROTATE_180: matriz.postRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matriz.postRotate(270); break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matriz.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:   matriz.postScale(1, -1); break;
            default: return bitmap;
        }
        Bitmap rotado = Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matriz, true);
        if (rotado != bitmap) {
            bitmap.recycle();
        }
        return rotado;
    }

    /** Miniatura pequeña para la vista previa de la barra de entrada. */
    @Nullable
    public static Bitmap miniatura(@Nullable String base64) {
        if (base64 == null) {
            return null;
        }
        byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
        BitmapFactory.Options opciones = new BitmapFactory.Options();
        opciones.inSampleSize = 4;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opciones);
    }
}
