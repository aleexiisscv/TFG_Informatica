package com.smartfridge.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartfridge.dto.SensorResponse;
import com.smartfridge.repository.SensorRepository;

import lombok.RequiredArgsConstructor;

/** Estado "actual" de los 4 sensores. Para históricos, ver /api/registros o LecturaSensorRepository. */
@RestController
@RequestMapping("/api/sensores")
@RequiredArgsConstructor
public class SensorController {

    private final SensorRepository sensorRepository;

    @GetMapping
    public List<SensorResponse> listar() {
        return sensorRepository.findAll().stream()
                .map(s -> new SensorResponse(s.getTipo(), s.getMedicion(), s.getUltLectura()))
                .toList();
    }
}
