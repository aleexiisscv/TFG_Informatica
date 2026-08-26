package com.smartfridge.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartfridge.exception.InventarioVacioException;
import com.smartfridge.exception.ProductoNoEncontradoException;
import com.smartfridge.model.Inventario;
import com.smartfridge.model.Producto;
import com.smartfridge.repository.InventarioRepository;
import com.smartfridge.repository.ProductoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class InventarioServiceImpl implements InventarioService {

    private final ProductoRepository productoRepository;
    private final InventarioRepository inventarioRepository;
    private final RegistroService registroService;

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
}
