package com.smartfridge.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartfridge.dto.RegistroResponse;
import com.smartfridge.model.Registro;
import com.smartfridge.model.TipoRegistro;
import com.smartfridge.repository.RegistroRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/registros")
@RequiredArgsConstructor
public class RegistroController {

    private final RegistroRepository registroRepository;

    /** GET /api/registros?tipo=ALERTA para, p. ej., pintar solo el panel de notificaciones. */
    @GetMapping
    public List<RegistroResponse> listar(@RequestParam(required = false) TipoRegistro tipo) {
        List<Registro> registros = (tipo != null)
                ? registroRepository.findByTipoRegistroOrderByFechaDesc(tipo)
                : registroRepository.findAll();

        return registros.stream().map(RegistroController::toResponse).toList();
    }

    private static RegistroResponse toResponse(Registro registro) {
        return new RegistroResponse(
                registro.getId(),
                registro.getTipoRegistro(),
                registro.getProducto() != null ? registro.getProducto().getNombre() : null,
                registro.getSensor() != null ? registro.getSensor().getTipo() : null,
                registro.getMedicion(),
                registro.getFecha()
        );
    }
}
