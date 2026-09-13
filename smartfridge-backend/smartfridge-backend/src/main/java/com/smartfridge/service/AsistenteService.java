package com.smartfridge.service;

import com.smartfridge.dto.ChatRequest;
import com.smartfridge.dto.ChatResponse;

/**
 * Asistente conversacional del frigorífico (Fase 11).
 *
 * <p>Implementa un patrón <b>RAG</b> (Retrieval-Augmented Generation)
 * en su forma más directa: en lugar de una búsqueda semántica sobre una
 * base vectorial, la "recuperación" es una consulta al propio modelo de
 * datos del frigorífico. Y es la correcta para este dominio: el corpus
 * no son miles de documentos entre los que hay que encontrar los
 * relevantes, sino unas pocas decenas de filas que caben enteras en el
 * prompt. Montar embeddings aquí añadiría infraestructura, latencia y
 * una fuente de error (recuperar el fragmento equivocado) sin mejorar
 * nada: el contexto completo ya cabe.</p>
 *
 * <p>Lo que sí se conserva de RAG es lo esencial: el modelo responde
 * sobre datos verificables recuperados en tiempo real, no sobre lo que
 * memorizó durante su entrenamiento.</p>
 */
public interface AsistenteService {

    /**
     * Responde a una consulta del usuario con el estado actual del
     * frigorífico inyectado en el prompt.
     *
     * @throws com.smartfridge.exception.GeminiApiException si el modelo
     *         no está disponible
     */
    ChatResponse responder(ChatRequest peticion);
}
