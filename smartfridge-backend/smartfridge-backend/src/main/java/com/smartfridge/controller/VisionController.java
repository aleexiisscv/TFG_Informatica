package com.smartfridge.controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smartfridge.dto.InventarioResponse;
import com.smartfridge.exception.ProductoNoIdentificadoException;
import com.smartfridge.model.Inventario;
import com.smartfridge.service.InventarioService;
import com.smartfridge.service.VisionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Punto de entrada del flujo "Cloud Vision AI" (Fase 9): la ESP32-CAM
 * sube aquí la fotografía tomada en cada apertura del frigorífico.
 * Sustituye por completo al flujo RFID (MFRC522) como origen de las
 * altas de inventario — ver la justificación arquitectónica completa
 * (por qué se delega la inferencia a un modelo Cloud en vez de a
 * TensorFlow Lite embebido en la ESP32) en la respuesta del chat / la
 * memoria del TFG.
 *
 * Abierto en SecurityConfig sin necesidad de tocar esa clase: cae bajo
 * el {@code permitAll()} ya existente de "/api/**", el mismo que usan
 * el resto de endpoints consumidos por dispositivos no interactivos.
 */
@RestController
@RequestMapping("/api/vision")
@RequiredArgsConstructor
@Slf4j
public class VisionController {

    private static final String DESCONOCIDO = "DESCONOCIDO";

    private final VisionService visionService;
    private final InventarioService inventarioService;

    @PostMapping(value = "/analizar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public InventarioResponse analizar(@RequestParam("imagen") MultipartFile imagen) throws IOException {
        String identificador = visionService.identificarProducto(imagen.getBytes(), imagen.getContentType());
        log.info("Gemini identificó la imagen recibida como '{}'", identificador);

        if (DESCONOCIDO.equals(identificador)) {
            throw new ProductoNoIdentificadoException();
        }

        // Orquestación: el identificador que devuelve Gemini sustituye
        // directamente al tag que antes emitía el lector MFRC522. Para
        // que esto funcione, el catálogo (tabla producto) debe darse de
        // alta con rfid_tag = identificador esperado de Gemini (p. ej.
        // "brick_leche"), no con el UID físico de una etiqueta RFID real.
        //
        // InventarioServiceImpl no necesita ningún cambio: no sabe ni le
        // importa si el String que recibe vino de un lector RFID o de un
        // modelo de visión (principio abierto/cerrado). Si el producto no
        // existe en el catálogo lanzará ProductoNoEncontradoException, que
        // GlobalExceptionHandler YA traduce a 404 — no se vuelve a
        // capturar aquí, para no duplicar un manejo de errores que el
        // @RestControllerAdvice ya centraliza (DRY).
        Inventario unidad = inventarioService.anadirProducto(identificador);
        return toResponse(unidad);
    }

    private static InventarioResponse toResponse(Inventario inventario) {
        return new InventarioResponse(
                inventario.getId(),
                inventario.getProducto().getRfidTag(),
                inventario.getProducto().getNombre(),
                inventario.getProducto().getNutriScore(),
                inventario.getFechaEntrada(),
                inventario.getFechaCaducidad()
        );
    }
}
