package com.smartfridge.service;

import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartfridge.gemini.GeminiClient;
import com.smartfridge.gemini.GeminiMensaje;
import com.smartfridge.gemini.GeminiOpciones;
import com.smartfridge.gemini.ImagenEntrante;
import com.smartfridge.model.Producto;
import com.smartfridge.repository.ProductoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Identificación de producto por visión.
 *
 * <h2>Fase 13 — de texto libre a salida estructurada</h2>
 * Hasta ahora el modelo devolvía texto libre y se le pedía <i>por
 * favor</i>, en el prompt, que usara snake_case. Funcionaba casi
 * siempre, y ese "casi" era el problema: ante la misma foto podía
 * responder {@code brick_leche}, {@code leche_entera_asturiana} o
 * {@code leche}. Solo la primera existía en el catálogo, así que las
 * otras dos terminaban en un 404 de {@code ProductoNoEncontrado} — un
 * fallo silencioso y no reproducible, que es la peor clase de fallo.
 *
 * <p>Ahora la petición lleva un <b>esquema de respuesta</b> de tipo
 * enumerado construido con los {@code rfid_tag} que existen realmente en
 * la tabla {@code producto}. La diferencia es de naturaleza, no de
 * grado:</p>
 * <ul>
 *   <li>Un <b>prompt</b> es una sugerencia. El modelo puede desatenderla,
 *       y lo hace más a menudo cuanto más ambigua es la imagen — que es
 *       justo cuando peor viene.</li>
 *   <li>Un <b>esquema</b> es una restricción del decodificador: los
 *       tokens que no encajan en la gramática del enum <b>no se pueden
 *       generar</b>. El espacio de respuestas posibles deja de ser
 *       "cualquier cadena" y pasa a ser "una de estas N".</li>
 * </ul>
 *
 * <p>Consecuencia práctica: el contrato entre visión e inventario deja
 * de ser una convención de texto y pasa a ser una <b>clave foránea</b>.
 * Lo que devuelve este servicio existe en el catálogo por construcción,
 * no por suerte.</p>
 *
 * <p>Queda una alucinación que el esquema NO evita, y conviene decirlo:
 * el modelo sigue pudiendo elegir el valor <i>equivocado</i> de la lista
 * —confundir dos marcas de leche— pero ya no puede inventarse un
 * identificador que no existe. Se acota el problema de "cadena
 * arbitraria" a "clasificación errónea entre opciones válidas", que es
 * medible, reproducible y corregible con mejores fotos o mejores
 * nombres de producto.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VisionServiceImpl implements VisionService {

    private static final String DESCONOCIDO = "DESCONOCIDO";

    /**
     * Tope del enum enviado al modelo. Con catálogos grandes, la lista de
     * valores empieza a ocupar una parte apreciable del prompt en CADA
     * petición. Para un TFG (decenas de productos) sobra de largo; el
     * aviso queda para que el día que se supere no pase inadvertido.
     */
    private static final int MAX_VALORES_ENUM = 300;

    /**
     * Ver la nota sobre modelos "thinking" en {@link GeminiOpciones}: la
     * respuesta real es un solo valor del enum, pero el razonamiento
     * interno se descuenta del mismo presupuesto.
     */
    private static final double TEMPERATURA = 0.0;
    private static final int MAX_TOKENS = 512;

    private final GeminiClient geminiClient;
    private final ProductoRepository productoRepository;

    /**
     * {@code readOnly}: solo se consulta el catálogo. La transacción
     * envuelve la lectura para que los productos estén cargados antes de
     * construir el enum.
     */
    @Override
    @Transactional(readOnly = true)
    public String identificarProducto(byte[] imagenBytes, String mimeType) {
        List<Producto> catalogo = productoRepository.findAll();

        if (catalogo.isEmpty()) {
            // Sin catálogo, el enum solo podría contener DESCONOCIDO y la
            // llamada sería un gasto de cuota con resultado conocido de
            // antemano. Se corta aquí: el controlador lo traducirá a un
            // 422 con un mensaje que el usuario puede entender.
            log.warn("El catálogo de productos está vacío: no se consulta a Gemini. "
                    + "Da de alta productos en /api/productos antes de usar la visión.");
            return DESCONOCIDO;
        }

        List<String> valores = valoresDelEnum(catalogo);
        String base64 = Base64.getEncoder().encodeToString(imagenBytes);
        String mime = ImagenEntrante.normalizarMime(mimeType);

        String respuesta = geminiClient.generar(
                construirPrompt(catalogo),
                List.of(GeminiMensaje.usuarioConImagen(
                        "Identifica el producto de esta fotografía.", mime, base64)),
                GeminiOpciones.deEnum(TEMPERATURA, MAX_TOKENS, valores));

        return validar(respuesta, valores);
    }

    /**
     * Los valores admitidos son los identificadores REALES del catálogo,
     * más {@code DESCONOCIDO} como escapatoria.
     *
     * <p>Esa escapatoria es imprescindible: sin ella, el modelo estaría
     * obligado a elegir un producto ante una foto de una pared, y el
     * sistema daría de alta en el inventario algo que nadie ha metido en
     * el frigorífico. Un "no lo sé" explícito es información; una
     * respuesta forzada es basura con formato correcto.</p>
     */
    private List<String> valoresDelEnum(List<Producto> catalogo) {
        if (catalogo.size() > MAX_VALORES_ENUM) {
            log.warn("El catálogo tiene {} productos y solo se envían los {} primeros al modelo. "
                    + "Conviene revisar esta estrategia (p. ej. filtrar por categoría).",
                    catalogo.size(), MAX_VALORES_ENUM);
        }
        List<String> valores = catalogo.stream()
                .map(Producto::getRfidTag)
                .filter(tag -> tag != null && !tag.isBlank())
                .distinct()
                .limit(MAX_VALORES_ENUM)
                .collect(Collectors.toCollection(java.util.ArrayList::new));
        valores.add(DESCONOCIDO);
        return valores;
    }

    /**
     * El prompt deja de tener que enseñar un formato —de eso se encarga
     * ahora el esquema— y se dedica a lo único que el esquema no puede
     * dar: el <b>significado</b> de cada identificador.
     *
     * <p>Sin esta correspondencia, el modelo tendría que adivinar que
     * {@code brick_leche_entera} es un cartón de leche a partir del
     * propio identificador. Dándole el nombre comercial junto al id, la
     * tarea pasa de "descifrar una cadena" a "emparejar lo que veo con
     * una lista de productos", que es mucho más fácil y mucho más
     * fiable.</p>
     */
    private String construirPrompt(List<Producto> catalogo) {
        String listado = catalogo.stream()
                .limit(MAX_VALORES_ENUM)
                .map(p -> "- " + p.getRfidTag() + " → " + p.getNombre())
                .collect(Collectors.joining("\n"));

        return """
                Eres el sistema de visión de un frigorífico inteligente. En la imagen verás un \
                producto de supermercado.

                Debes decidir cuál de los productos del catálogo aparece en la fotografía y \
                responder con su identificador exacto.

                CATÁLOGO (identificador → nombre del producto):
                %s
                - DESCONOCIDO → ninguno de los anteriores

                Reglas:
                1. Si reconoces con seguridad razonable uno de los productos del catálogo, \
                responde con su identificador.
                2. Si en la imagen no hay ningún producto, no se distingue, o es un producto \
                que NO está en el catálogo, responde DESCONOCIDO.
                3. Ante la duda entre "un producto parecido del catálogo" y DESCONOCIDO, elige \
                DESCONOCIDO: dar de alta el producto equivocado ensucia el inventario y el \
                usuario no tiene forma de darse cuenta.""".formatted(listado);
    }

    /**
     * Comprobación de cinturón y tirantes.
     *
     * <p>El esquema hace que esto no deba fallar nunca. Se valida
     * igualmente porque el coste es una búsqueda en un {@code Set} y la
     * alternativa —confiar— significa que un cambio futuro en la API de
     * Gemini, o un modelo de respaldo que ignore el esquema, se
     * manifestaría como un 404 confuso en vez de como un aviso claro en
     * el log.</p>
     */
    private String validar(String respuesta, List<String> valores) {
        String limpio = respuesta == null ? "" : respuesta.trim();
        Set<String> admitidos = Set.copyOf(valores);
        if (admitidos.contains(limpio)) {
            return limpio;
        }
        log.warn("Gemini devolvió '{}', que NO pertenece al enum enviado ({} valores). "
                + "Se trata como DESCONOCIDO.", limpio, valores.size());
        return DESCONOCIDO;
    }
}
