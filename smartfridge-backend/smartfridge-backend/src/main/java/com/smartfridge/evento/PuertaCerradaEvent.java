package com.smartfridge.evento;

import java.time.Instant;

/**
 * La puerta del frigorífico acaba de cerrarse.
 *
 * <h2>Por qué un evento y no una llamada directa</h2>
 * El {@code FrigorificoTopicRouter} es una capa anticorrupción: su
 * trabajo es traducir el formato "de cable" del ESP32 al dominio, y
 * nada más. Si llamara directamente al servicio de captura, ese router
 * pasaría a saber que existe una cámara, cómo se dispara y qué hacer si
 * falla — y el día que se añada un segundo consumidor del cierre de
 * puerta (apagar una luz, registrar cuánto tiempo estuvo abierta)
 * habría que volver a tocarlo.
 *
 * <p>Con un evento de aplicación, el router solo anuncia un hecho del
 * dominio. Quién escuche, cuántos escuchen y si lo hacen de forma
 * síncrona o asíncrona deja de ser asunto suyo.</p>
 *
 * @param momento instante en que se detectó el cierre
 */
public record PuertaCerradaEvent(Instant momento) {

    public static PuertaCerradaEvent ahora() {
        return new PuertaCerradaEvent(Instant.now());
    }
}
