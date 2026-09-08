package com.smartfridge.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartfridge.exception.InventarioVacioException;
import com.smartfridge.exception.ProductoNoEncontradoException;
import com.smartfridge.model.Inventario;
import com.smartfridge.model.Producto;
import com.smartfridge.model.TipoRegistro;
import com.smartfridge.repository.InventarioRepository;
import com.smartfridge.repository.ProductoRepository;
import com.smartfridge.repository.RegistroRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class InventarioServiceImpl implements InventarioService {

    private final ProductoRepository productoRepository;
    private final InventarioRepository inventarioRepository;
    private final RegistroRepository registroRepository;
    private final RegistroService registroService;

    /**
     * Días de antelación con los que se avisa. Configurable porque el
     * valor razonable depende de los hábitos de quien use el frigorífico:
     * dos días sirven para una compra semanal, y se quedan cortos para
     * quien compra una vez al mes.
     */
    @Value("${smartfridge.caducidades.dias-margen:2}")
    private int diasMargen;

    /**
     * {@code @Transactional} aquí cubre tres operaciones relacionadas
     * (buscar producto, guardar la unidad de inventario, auditar la
     * entrada): si la auditoría fallara, no queremos una unidad de
     * inventario "fantasma" sin su Registro correspondiente — el mismo
     * argumento de consistencia que en {@code SensorServiceImpl},
     * aplicado ahora a nivel de caso de uso de negocio en vez de a
     * nivel de sensor.
     */
    @Override
    @Transactional
    public Inventario anadirProducto(String rfidTag) {
        Producto producto = productoRepository.findById(rfidTag)
                .orElseThrow(() -> new ProductoNoEncontradoException(rfidTag));

        LocalDateTime ahora = LocalDateTime.now();
        Inventario unidad = Inventario.builder()
                .producto(producto)
                .fechaEntrada(ahora)
                .fechaCaducidad(ahora.plusDays(producto.getPlazoCaducidadDias()))
                .build();

        Inventario guardada = inventarioRepository.save(unidad);
        registroService.registrarEntrada(producto);

        log.info("Producto '{}' (RFID={}) añadido al inventario. Caduca: {}",
                producto.getNombre(), rfidTag, guardada.getFechaCaducidad());
        return guardada;
    }

    @Override
    @Transactional
    public Inventario retirarProducto(String rfidTag) {
        Inventario unidad = inventarioRepository
                .findFirstByProducto_RfidTagOrderByFechaEntradaAsc(rfidTag)
                .orElseThrow(() -> new InventarioVacioException(rfidTag));

        Producto producto = unidad.getProducto();
        inventarioRepository.delete(unidad);
        registroService.registrarSalida(producto);

        log.info("Producto '{}' (RFID={}) retirado del inventario (unidad más antigua, FIFO).",
                producto.getNombre(), rfidTag);
        return unidad;
    }

    // ------------------------------------------------------------------
    // Motor de caducidades (Fase 13)
    // ------------------------------------------------------------------

    /**
     * Tarea diaria que convierte una fecha guardada en base de datos en
     * un aviso que el usuario ve.
     *
     * <p>Hasta ahora {@code fecha_caducidad} solo se pintaba si el
     * usuario abría el inventario. Es decir: el sistema sabía que la
     * leche caducaba mañana y no se lo decía a nadie. Este proceso cierra
     * esa brecha — es la diferencia entre almacenar datos y prestar un
     * servicio.</p>
     *
     * <p><b>Por qué a las 8:00 y no cada hora.</b> La caducidad se mide
     * en días: comprobarla con más frecuencia no adelanta ninguna
     * información, solo multiplica escrituras. Y a primera hora porque es
     * cuando el aviso todavía sirve para decidir el desayuno o la compra;
     * a las once de la noche llega tarde.</p>
     *
     * <p>La zona horaria se fija explícitamente: sin ella, el cron usaría
     * la del sistema, y un despliegue en la nube casi siempre corre en
     * UTC. Las "8:00" se convertirían en las 10:00 en España durante el
     * horario de verano.</p>
     */
    @Override
    @Scheduled(cron = "${smartfridge.caducidades.cron:0 0 8 * * *}", zone = "Europe/Madrid")
    @Transactional
    public int revisarCaducidades() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limite = ahora.plusDays(diasMargen);
        LocalDateTime inicioDeHoy = LocalDate.now().atStartOfDay();

        List<Inventario> proximas =
                inventarioRepository.findByFechaCaducidadLessThanEqualOrderByFechaCaducidadAsc(limite);

        // Se agrupa por producto y se conserva la caducidad MÁS PRÓXIMA.
        // Seis yogures del mismo lote son un solo aviso, no seis: el
        // usuario necesita saber que "los yogures caducan", no recibir la
        // misma frase repetida media docena de veces.
        Map<String, Inventario> masUrgentePorProducto = new LinkedHashMap<>();
        for (Inventario unidad : proximas) {
            Producto producto = unidad.getProducto();
            if (producto == null || unidad.getFechaCaducidad() == null) {
                continue;
            }
            masUrgentePorProducto.putIfAbsent(producto.getRfidTag(), unidad);
        }

        int creadas = 0;
        for (Inventario unidad : masUrgentePorProducto.values()) {
            Producto producto = unidad.getProducto();

            if (registroRepository.existsByTipoRegistroAndProducto_RfidTagAndFechaGreaterThanEqual(
                    TipoRegistro.ALERTA, producto.getRfidTag(), inicioDeHoy)) {
                log.debug("Ya existe una alerta de hoy para '{}'; no se duplica", producto.getRfidTag());
                continue;
            }

            long dias = ChronoUnit.DAYS.between(
                    ahora.toLocalDate(), unidad.getFechaCaducidad().toLocalDate());
            registroService.registrarAlertaCaducidad(producto, dias);
            creadas++;

            log.info("Alerta de caducidad para '{}': {}",
                    producto.getNombre(),
                    dias < 0 ? "caducado hace " + (-dias) + " día(s)" : "caduca en " + dias + " día(s)");
        }

        log.info("Revisión de caducidades completada: {} productos en riesgo, {} alertas nuevas",
                masUrgentePorProducto.size(), creadas);
        return creadas;
    }
}
