package com.smartfridge.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartfridge.dto.ChatRequest;
import com.smartfridge.dto.ChatResponse;
import com.smartfridge.service.AsistenteService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Punto de entrada del asistente conversacional (Fase 11).
 *
 * <p>Controlador deliberadamente fino: valida la entrada con
 * {@code @Valid} y delega. No conoce Gemini, ni el formato del contexto,
 * ni el manejo de errores del modelo — de eso último se encarga
 * {@code GlobalExceptionHandler}, que ya traduce
 * {@code GeminiApiException} a 502 desde la Fase 9. Reaprovechar ese
 * mapeo en lugar de capturar aquí mantiene el formato de error idéntico
 * en toda la API.</p>
 *
 * <p>Queda bajo el {@code permitAll()} de "/api/**" de
 * {@code SecurityConfig}, igual que el resto de endpoints. Merece una
 * nota para la memoria: este endpoint consume cuota de pago de un
 * servicio externo, así que es el candidato MÁS claro a exigir
 * autenticación en cuanto se implemente JWT — un endpoint de IA abierto
 * es una factura abierta.</p>
 */
@RestController
@RequestMapping("/api/asistente")
@RequiredArgsConstructor
public class AsistenteController {

    private final AsistenteService asistenteService;

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest peticion) {
        return asistenteService.responder(peticion);
    }
}
