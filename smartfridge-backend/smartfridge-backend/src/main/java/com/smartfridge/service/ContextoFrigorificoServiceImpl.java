package com.smartfridge.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartfridge.dto.ContextoResumen;
import com.smartfridge.model.Inventario;
import com.smartfridge.model.Producto;
import com.smartfridge.model.Registro;
import com.smartfridge.model.Sensor;
import com.smartfridge.model.TipoRegistro;
import com.smartfridge.model.TipoSensor;
import com.smartfridge.repository.InventarioRepository;
import com.smartfridge.repository.ProductoRepository;
import com.smartfridge.repository.RegistroRepository;
import com.smartfridge.repository.SensorRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serializa el estado del frigorífico al bloque de texto que consume el
 * asistente.
 *
 * <h2>Por qué texto delimitado y no JSON</h2>
 * Un LLM lee ambos, pero el JSON gasta entre un 20 % y un 30 % más de
 * tokens en llaves, comillas y nombres de campo repetidos en cada
 * elemento — y esos tokens se pagan en CADA turno de conversación,
 * porque el contexto se reinyecta entero cada vez. El formato de líneas
 * con cabeceras explícitas transmite la misma información y deja más
 * presupuesto para la respuesta.
 *
 * <h2>Las cuatro decisiones que evitan alucinaciones</h2>
 * <ol>
 *   <li><b>Los días hasta la caducidad se calculan AQUÍ.</b> Los LLM
 *       razonan mal con aritmética de fechas: dado "2026-08-31" y "hoy
 *       es 2026-08-29" es perfectamente capaz de decir "caduca en 3
 *       días". Se le da "caduca en 2 días" ya resuelto y se le prohíbe
 *       recalcularlo.</li>
 *   <li><b>Las secciones vacías se escriben igualmente</b>, con un
 *       "(vacío)" explícito. Omitir una sección deja un hueco que el
 *       modelo rellena con lo que le parece plausible; decirle "el
 *       frigorífico está vacío" no deja margen.</li>
 *   <li><b>El catálogo va en su propia sección</b>, rotulada como NO
 *       disponible. Mezclarlo con el inventario sería la vía más rápida
 *       a que sugiera una tortilla con los huevos que ya se comió.</li>
 *   <li><b>Los nombres de producto se sanean.</b> Vienen, en última
 *       instancia, de lo que un modelo de visión leyó en una etiqueta:
 *       un salto de línea o un texto tipo "ignora las instrucciones
 *       anteriores" dentro de un nombre podría romper la estructura del
 *       bloque o intentar una inyección de prompt. Se aplanan a una
 *       línea y se recortan.</li>
 * </ol>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ContextoFrigorificoServiceImpl implements ContextoFrigorificoService {

    /** Cotas del prompt: el contexto viaja en cada turno, así que no puede crecer sin límite. */
    private static final int MAX_LINEAS_INVENTARIO = 40;
    private static final int MAX_LINEAS_CATALOGO = 30;
    private static final int MAX_ALERTAS = 5;
    private static final int MAX_LONGITUD_NOMBRE = 80;

    /** Umbral en días a partir del cual se marca un producto como urgente. */
    private static final long DIAS_CONSUMIR_YA = 2;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final InventarioRepository inventarioRepository;
    private final ProductoRepository productoRepository;
    private final SensorRepository sensorRepository;
    private final RegistroRepository registroRepository;

    /**
     * {@code @Transactional(readOnly = true)} no es decorativo: las
     * relaciones {@code Inventario.producto} y {@code Registro.sensor}
     * son LAZY y el proyecto tiene {@code open-in-view: false}. Sin una
     * transacción abierta durante todo el recorrido, el primer
     * {@code getProducto().getNombre()} sobre una entidad ya desligada
     * lanzaría {@code LazyInitializationException}. {@code readOnly}
     * además permite a Hibernate saltarse el "dirty checking" al cerrar,
     * que aquí no aporta nada porque no se escribe.
     */
    @Override
    @Transactional(readOnly = true)
    public ContextoFrigorifico capturar() {
        LocalDateTime ahora = LocalDateTime.now();

        List<Inventario> unidades = inventarioRepository.findAll();
        List<LineaInventario> inventario = agrupar(unidades, ahora.toLocalDate());
        List<Sensor> sensores = sensorRepository.findAll();
        List<Registro> alertas = registroRepository
                .findByTipoRegistroOrderByFechaDesc(TipoRegistro.ALERTA);
        List<Producto> catalogoNoDisponible = catalogoFueraDelFrigorifico(unidades);

        String texto = String.join("\n",
                bloqueInventario(inventario),
                "",
                bloqueSensores(sensores, ahora),
                "",
                bloqueAlertas(alertas),
                "",
                bloqueCatalogo(catalogoNoDisponible));

        ContextoResumen resumen = new ContextoResumen(
                unidades.size(),
                inventario.size(),
                catalogoNoDisponible.size(),
                sensores.size(),
                Math.min(alertas.size(), MAX_ALERTAS));

        log.debug("Contexto capturado para el asistente: {}", resumen);
        return new ContextoFrigorifico(texto, resumen);
    }

    // ------------------------------------------------------------------
    // Inventario
    // ------------------------------------------------------------------

    /**
     * Agrupa las unidades físicas por producto. El inventario guarda una
     * fila por unidad (dos bricks de leche = dos filas), pero al modelo
     * le sirve mejor "Leche (x2)" que dos líneas idénticas: ocupa menos
     * tokens y evita que interprete duplicados como productos distintos.
     * De cada grupo se conserva la caducidad MÁS PRÓXIMA, que es la que
     * determina la urgencia.
     */
    private List<LineaInventario> agrupar(List<Inventario> unidades, LocalDate hoy) {
        Map<String, LineaInventario> porProducto = new LinkedHashMap<>();

        for (Inventario unidad : unidades) {
            Producto producto = unidad.getProducto();
            if (producto == null) {
                continue;
            }
            String clave = producto.getRfidTag();
            LineaInventario actual = porProducto.get(clave);

            Long dias = unidad.getFechaCaducidad() == null
                    ? null
                    : ChronoUnit.DAYS.between(hoy, unidad.getFechaCaducidad().toLocalDate());

            if (actual == null) {
                porProducto.put(clave, new LineaInventario(
                        sanear(producto.getNombre()),
                        producto.getNutriScore(),
                        1,
                        dias,
                        unidad.getFechaCaducidad()));
            } else {
                porProducto.put(clave, actual.sumarUnidad(dias, unidad.getFechaCaducidad()));
            }
        }

        // Lo más urgente primero: es lo que el asistente debe mencionar
        // antes si el usuario pregunta "¿qué hago con lo que tengo?".
        // Los productos sin fecha van al final (nulls last).
        return porProducto.values().stream()
                .sorted(Comparator.comparing(LineaInventario::diasHastaCaducidad,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private String bloqueInventario(List<LineaInventario> inventario) {
        StringBuilder sb = new StringBuilder();
        sb.append("### INVENTARIO ACTUAL — lo que HAY DENTRO del frigorífico en este momento\n");
        if (inventario.isEmpty()) {
            sb.append("(vacío: no hay ningún producto dentro del frigorífico ahora mismo)");
            return sb.toString();
        }
        int mostrados = Math.min(inventario.size(), MAX_LINEAS_INVENTARIO);
        for (int i = 0; i < mostrados; i++) {
            sb.append(inventario.get(i).aLinea()).append('\n');
        }
        if (inventario.size() > mostrados) {
            sb.append("(y ").append(inventario.size() - mostrados)
                    .append(" productos más, omitidos por longitud)\n");
        }
        return sb.toString().stripTrailing();
    }

    /**
     * Una línea de inventario ya lista para el prompt.
     *
     * @param diasHastaCaducidad negativo = caducado; null = sin fecha
     */
    private record LineaInventario(String nombre, Producto.NutriScore nutriScore, int unidades,
                                   Long diasHastaCaducidad, LocalDateTime fechaCaducidad) {

        LineaInventario sumarUnidad(Long dias, LocalDateTime fecha) {
            boolean nuevaEsMasUrgente = diasHastaCaducidad == null
                    || (dias != null && dias < diasHastaCaducidad);
            return new LineaInventario(
                    nombre,
                    nutriScore,
                    unidades + 1,
                    nuevaEsMasUrgente ? dias : diasHastaCaducidad,
                    nuevaEsMasUrgente ? fecha : fechaCaducidad);
        }

        String aLinea() {
            StringBuilder sb = new StringBuilder("- ").append(nombre);
            if (unidades > 1) {
                sb.append(" (x").append(unidades).append(')');
            }
            sb.append(" | Nutri-Score: ")
                    .append(nutriScore == null ? "no informado" : nutriScore.name());
            sb.append(" | ").append(textoCaducidad());
            return sb.toString();
        }

        private String textoCaducidad() {
            if (diasHastaCaducidad == null || fechaCaducidad == null) {
                return "sin fecha de caducidad registrada";
            }
            String fecha = fechaCaducidad.format(FECHA);
            if (diasHastaCaducidad < 0) {
                return "CADUCADO hace " + (-diasHastaCaducidad) + " día(s) (el " + fecha
                        + ") — NO recomendar su consumo";
            }
            if (diasHastaCaducidad == 0) {
                return "CADUCA HOY (" + fecha + ") — [CONSUMIR YA]";
            }
            String base = "caduca en " + diasHastaCaducidad + " día(s) (el " + fecha + ")";
            return diasHastaCaducidad <= DIAS_CONSUMIR_YA ? base + " — [CONSUMIR YA]" : base;
        }
    }

    // ------------------------------------------------------------------
    // Sensores
    // ------------------------------------------------------------------

    private String bloqueSensores(List<Sensor> sensores, LocalDateTime ahora) {
        StringBuilder sb = new StringBuilder();
        sb.append("### SENSORES — última lectura de cada sensor del frigorífico\n");
        if (sensores.isEmpty()) {
            sb.append("(sin lecturas: los sensores todavía no han reportado nada)");
            return sb.toString();
        }
        for (Sensor sensor : sensores) {
            sb.append("- ").append(etiqueta(sensor.getTipo())).append(": ")
                    .append(valorLegible(sensor.getTipo(), sensor.getMedicion()))
                    .append(" (").append(antiguedad(sensor.getUltLectura(), ahora)).append(")\n");
        }
        return sb.toString().stripTrailing();
    }

    private String etiqueta(TipoSensor tipo) {
        if (tipo == null) {
            return "Sensor desconocido";
        }
        return switch (tipo) {
            case TEMPERATURA -> "Temperatura interior";
            case HUMEDAD -> "Humedad interior";
            case PUERTA -> "Puerta";
            case AGUA -> "Sensor de agua";
        };
    }

    /**
     * Traduce el valor numérico a lenguaje natural. Los sensores binarios
     * llegan como 1.0/0.0 desde {@code FrigorificoTopicRouter}
     * (1.0 = "Puerta abierta" / "Agua detectada"); pasarle al modelo un
     * "PUERTA: 1.0" le obligaría a adivinar la convención, y adivinar es
     * exactamente lo que queremos evitar.
     */
    private String valorLegible(TipoSensor tipo, Float medicion) {
        if (tipo == null || medicion == null) {
            return "sin dato";
        }
        return switch (tipo) {
            case TEMPERATURA -> String.format(Locale.ROOT, "%.1f °C", medicion);
            case HUMEDAD -> String.format(Locale.ROOT, "%.0f %% de humedad relativa", medicion);
            case PUERTA -> esUno(medicion) ? "ABIERTA" : "cerrada";
            case AGUA -> esUno(medicion) ? "AGUA DETECTADA (posible fuga)" : "sin agua detectada";
        };
    }

    private boolean esUno(Float medicion) {
        return medicion != null && medicion >= 0.5f;
    }

    /**
     * Antigüedad relativa además de la fecha absoluta. Al modelo le sirve
     * para calibrar la confianza: "hace 4 horas" merece un matiz que
     * "hace 30 segundos" no necesita.
     */
    private String antiguedad(LocalDateTime lectura, LocalDateTime ahora) {
        if (lectura == null) {
            return "sin marca de tiempo";
        }
        long minutos = Duration.between(lectura, ahora).toMinutes();
        String relativo;
        if (minutos < 1) {
            relativo = "hace menos de 1 minuto";
        } else if (minutos < 60) {
            relativo = "hace " + minutos + " min";
        } else if (minutos < 60 * 24) {
            relativo = "hace " + (minutos / 60) + " h";
        } else {
            relativo = "hace " + (minutos / (60 * 24)) + " día(s)";
        }
        return relativo + ", " + lectura.format(FECHA_HORA);
    }

    // ------------------------------------------------------------------
    // Alertas
    // ------------------------------------------------------------------

    private String bloqueAlertas(List<Registro> alertas) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ALERTAS RECIENTES — incidencias registradas por los sensores\n");
        if (alertas.isEmpty()) {
            sb.append("(ninguna alerta registrada)");
            return sb.toString();
        }
        alertas.stream().limit(MAX_ALERTAS).forEach(alerta -> {
            String tipo = alerta.getSensor() != null && alerta.getSensor().getTipo() != null
                    ? alerta.getSensor().getTipo().name()
                    : "SENSOR DESCONOCIDO";
            sb.append("- ").append(alerta.getFecha().format(FECHA_HORA))
                    .append(" — ").append(tipo);
            if (alerta.getMedicion() != null) {
                sb.append(" (valor registrado: ")
                        .append(String.format(Locale.ROOT, "%.1f", alerta.getMedicion())).append(')');
            }
            sb.append('\n');
        });
        return sb.toString().stripTrailing();
    }

    // ------------------------------------------------------------------
    // Catálogo
    // ------------------------------------------------------------------

    /**
     * Productos dados de alta en el catálogo de los que NO queda ninguna
     * unidad dentro. Sirven para que el asistente pueda decir "esto lo
     * sueles tener, pero ahora no queda" en vez de tratarlo como
     * desconocido — siempre que el prompt deje claro que no está
     * disponible, cosa que hace la cabecera de esta sección.
     */
    private List<Producto> catalogoFueraDelFrigorifico(List<Inventario> unidades) {
        Set<String> dentro = unidades.stream()
                .map(Inventario::getProducto)
                .filter(p -> p != null)
                .map(Producto::getRfidTag)
                .collect(Collectors.toSet());

        return productoRepository.findAll().stream()
                .filter(p -> !dentro.contains(p.getRfidTag()))
                .toList();
    }

    private String bloqueCatalogo(List<Producto> catalogo) {
        StringBuilder sb = new StringBuilder();
        sb.append("### CATÁLOGO CONOCIDO — productos registrados que NO están dentro del frigorífico ahora\n");
        sb.append("(referencia únicamente; NINGUNO de estos está disponible para cocinar)\n");
        if (catalogo.isEmpty()) {
            sb.append("(vacío)");
            return sb.toString();
        }
        int mostrados = Math.min(catalogo.size(), MAX_LINEAS_CATALOGO);
        for (int i = 0; i < mostrados; i++) {
            Producto producto = catalogo.get(i);
            sb.append("- ").append(sanear(producto.getNombre()))
                    .append(" (Nutri-Score: ")
                    .append(producto.getNutriScore() == null ? "no informado" : producto.getNutriScore().name())
                    .append(")\n");
        }
        if (catalogo.size() > mostrados) {
            sb.append("(y ").append(catalogo.size() - mostrados).append(" más)\n");
        }
        return sb.toString().stripTrailing();
    }

    // ------------------------------------------------------------------

    /**
     * Aplana el nombre a una sola línea y lo recorta.
     *
     * <p>Los nombres del catálogo proceden, en el flujo de la Fase 9, de
     * lo que un modelo de visión leyó en el envase. Un salto de línea
     * rompería la estructura "una línea = un producto" del bloque, y un
     * nombre con texto tipo "### INVENTARIO ACTUAL" o "ignora las
     * instrucciones anteriores" sería un intento de inyección de prompt
     * a través de los datos. Aplanar y acotar no elimina el riesgo por
     * completo —para eso está también la regla explícita del system
     * prompt de tratar el bloque como datos— pero cierra el vector más
     * evidente.</p>
     */
    private String sanear(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "(producto sin nombre)";
        }
        String limpio = nombre.replaceAll("[\\r\\n#]+", " ").trim();
        return limpio.length() <= MAX_LONGITUD_NOMBRE
                ? limpio
                : limpio.substring(0, MAX_LONGITUD_NOMBRE) + "…";
    }
}
