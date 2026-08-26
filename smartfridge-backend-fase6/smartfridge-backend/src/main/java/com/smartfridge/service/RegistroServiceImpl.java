package com.smartfridge.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartfridge.model.Producto;
import com.smartfridge.model.Registro;
import com.smartfridge.model.Sensor;
import com.smartfridge.model.TipoRegistro;
import com.smartfridge.model.TipoSensor;
import com.smartfridge.repository.RegistroRepository;
import com.smartfridge.repository.SensorRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class RegistroServiceImpl implements RegistroService {

    private final RegistroRepository registroRepository;
    private final SensorRepository sensorRepository;

    @Override
    @Transactional
    public Registro registrarEntrada(Producto producto) {
        return guardar(TipoRegistro.ENTRADA, producto, null, null);
    }

    @Override
    @Transactional
    public Registro registrarSalida(Producto producto) {
        return guardar(TipoRegistro.SALIDA, producto, null, null);
    }

    @Override
    @Transactional
    public Registro registrarAlerta(TipoSensor tipoSensor, Float medicion) {
        // findByTipo puede no devolver nada si todavía no ha llegado
        // ninguna lectura "normal" de ese sensor (caso poco probable en
        // producción, pero posible en un entorno de pruebas recién
        // desplegado). Se registra igualmente la alerta con sensor=null
        // antes que perder el evento.
        Sensor sensor = sensorRepository.findByTipo(tipoSensor).orElse(null);
        if (sensor == null) {
            log.warn("Alerta de tipo {} sin fila previa en 'sensores'; se guarda sin asociar sensor_id.", tipoSensor);
        }
        return guardar(TipoRegistro.ALERTA, null, sensor, medicion);
    }

    private Registro guardar(TipoRegistro tipo, Producto producto, Sensor sensor, Float medicion) {
        Registro registro = Registro.builder()
                .tipoRegistro(tipo)
                .producto(producto)
                .sensor(sensor)
                .medicion(medicion)
                .fecha(LocalDateTime.now())
                .build();

        Registro guardado = registroRepository.save(registro);
        log.debug("Registro guardado: tipo={}, producto={}, sensor={}, medicion={}",
                tipo, producto != null ? producto.getRfidTag() : null,
                sensor != null ? sensor.getTipo() : null, medicion);
        return guardado;
    }
}
