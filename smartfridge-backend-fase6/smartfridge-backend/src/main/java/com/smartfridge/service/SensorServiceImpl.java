package com.smartfridge.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartfridge.model.LecturaSensor;
import com.smartfridge.model.Sensor;
import com.smartfridge.model.TipoSensor;
import com.smartfridge.repository.LecturaSensorRepository;
import com.smartfridge.repository.SensorRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ver {@link SensorService} para el contrato. Esta clase materializa el
 * requisito crítico de la Fase 3: cada lectura debe reflejarse a la vez
 * en "estado actual" (Sensor) y en "histórico" (LecturaSensor), sin que
 * puedan quedar desincronizadas entre sí.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SensorServiceImpl implements SensorService {

    private final SensorRepository sensorRepository;
    private final LecturaSensorRepository lecturaSensorRepository;

    /**
     * {@code @Transactional} envuelve el upsert de Sensor y el insert
     * de LecturaSensor en una única transacción de base de datos: si
     * el segundo save fallara (por ejemplo, un timeout de red hacia
     * Supabase a mitad de la operación), Spring hace ROLLBACK también
     * del primero. Sin esta anotación, un fallo a mitad de camino
     * dejaría el "estado actual" del sensor actualizado pero sin el
     * histórico correspondiente — exactamente el tipo de inconsistencia
     * silenciosa que un modelo de IA entrenado sobre ese histórico no
     * podría detectar por sí solo. Se explica con más detalle en el chat,
     * para la memoria.
     */
    @Override
    @Transactional
    public Sensor registrarLectura(TipoSensor tipo, float valor) {
        LocalDateTime ahora = LocalDateTime.now();

        Sensor sensor = sensorRepository.findByTipo(tipo)
                .map(existente -> {
                    existente.setMedicion(valor);
                    existente.setUltLectura(ahora);
                    return existente;
                })
                .orElseGet(() -> Sensor.builder()
                        .tipo(tipo)
                        .medicion(valor)
                        .ultLectura(ahora)
                        .build());

        Sensor guardado = sensorRepository.save(sensor);

        LecturaSensor lectura = LecturaSensor.builder()
                .sensor(guardado)
                .tipoSensor(tipo)
                .valor(valor)
                .fecha(ahora)
                .build();
        lecturaSensorRepository.save(lectura);

        log.debug("Lectura registrada: tipo={}, valor={}, fecha={}", tipo, valor, ahora);

        return guardado;
    }
}
