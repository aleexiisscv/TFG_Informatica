package com.smartfridge.service;

/**
 * Recuperación del contexto ("R" de RAG) que alimenta al asistente.
 *
 * <p>Se define como interfaz propia, separada de {@code AsistenteService},
 * por una razón concreta: <b>qué sabe el asistente</b> y <b>cómo
 * conversa</b> cambian por motivos distintos y a ritmos distintos.
 * Añadir el histórico de consumo al contexto, o cambiar el formato de
 * serialización para gastar menos tokens, no debería obligar a tocar la
 * clase que construye la conversación con Gemini. Es el principio de
 * responsabilidad única aplicado a la frontera más volátil del sistema.</p>
 *
 * <p>Además, aislarlo así permite testear el contexto sin red: se puede
 * verificar que un producto caducado aparece marcado como tal
 * comparando cadenas, sin llamar ni una vez a la API de Gemini.</p>
 */
public interface ContextoFrigorificoService {

    /**
     * Captura el estado actual del frigorífico y lo serializa.
     *
     * <p>Se invoca en CADA turno de conversación, no una vez por sesión:
     * entre dos preguntas seguidas el usuario puede haber sacado la leche
     * o la temperatura puede haber subido. Un contexto cacheado sería una
     * fuente de respuestas plausibles pero falsas.</p>
     */
    ContextoFrigorifico capturar();
}
