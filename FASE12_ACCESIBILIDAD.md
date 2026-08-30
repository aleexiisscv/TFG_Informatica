# Fase 12 — Accesibilidad, inclusión y chat multimodal

Tres vías de entrada (escribir, dictar, fotografiar), una salida por voz,
y una revisión de accesibilidad de toda la aplicación. Backend Spring
Boot + frontend Android.

---

## 0. Lo que ya existía

El **botón de micrófono con `RecognizerIntent` ya estaba** desde la fase
de rediseño de UI. Esta fase no lo duplica: lo refuerza (área táctil,
etiqueta, silenciado de la voz mientras se dicta) y lo integra con el
resto.

---

## 1. La decisión que define la fase: TTS frente a TalkBack

Un asistente que lee sus respuestas en voz alta y un lector de pantalla
activo **son dos sintetizadores hablando a la vez**. Es un fallo de
accesibilidad clásico, y especialmente irónico en una función pensada
para mejorarla.

`LectorDeVoz.hayLectorDePantalla()` comprueba dos condiciones:

```java
gestor.isEnabled() && gestor.isTouchExplorationEnabled()
```

Solo la primera también da positivo con servicios que no locutan nada —un
teclado de accesibilidad, por ejemplo—, y silenciar la voz por ese motivo
sería un falso positivo que dejaría al usuario sin la función.

**Comportamiento resultante:**

| Situación | Qué hace la app |
|---|---|
| Sin lector de pantalla, voz activada | Locuta con `TextToSpeech` |
| Sin lector de pantalla, voz desactivada | Nada |
| Con lector de pantalla | Cede el turno: `announceForAccessibility()` |

Con TalkBack activo la respuesta se anuncia con **la voz, el idioma y la
velocidad que esa persona ya ha configurado en su sistema**, que
casi siempre son muy distintos de los valores por defecto: quien usa
lector de pantalla a diario suele llevarlo a velocidades que a un oyente
no habituado le resultarían ininteligibles. Imponerle nuestro
sintetizador sería un retroceso, no una mejora.

El interruptor sigue disponible por si lo quiere igualmente, y la primera
vez se explica por qué viene apagado.

### Por qué la voz viene apagada por defecto

Reproducir audio sin que nadie lo haya pedido es, en sí mismo, un
problema de accesibilidad: sobresalta, interfiere con otro audio y puede
resultar embarazoso en público. La preferencia se **persiste en
`SharedPreferences`**, así que quien la necesita la activa una vez y no
vuelve a pensar en ella.

### El Markdown no se puede locutar

El backend pide a Gemini respuestas en Markdown porque su contenido
habitual son recetas. Un sintetizador leería *"asterisco asterisco huevos
asterisco asterisco"*.

La solución reutiliza el renderizador que ya existe:

```java
markwon.toMarkdown(markdown).toString()
```

`toMarkdown()` devuelve un `Spanned` donde el formato vive en *spans* y
no en caracteres, así que `toString()` da exactamente el texto que un
humano leería. **Cero código de parseo propio y cero riesgo de que la voz
y la pantalla digan cosas distintas.**

El system prompt colabora: se le prohíben tablas y listas anidadas
—estructuras que no se entienden escuchándolas— y se le pide escribir las
cantidades en palabras ("medio litro" mejor que "0,5 l").

---

## 2. Tres modos de interacción, cero permisos peligrosos

| Vía | Mecanismo | Permiso |
|---|---|---|
| Escribir | Teclado | — |
| Dictar | `RecognizerIntent` | **ninguno** |
| Fotografiar (cámara) | `TakePicturePreview` | **ninguno** |
| Fotografiar (galería) | `PickVisualMedia` | **ninguno** |

- El **dictado** delega en la aplicación de reconocimiento del sistema:
  graba ella, no nosotros, así que no hace falta `RECORD_AUDIO`.
- La **cámara** la abre otra aplicación mediante Intent: no se accede al
  sensor desde este proceso, así que no hace falta `CAMERA`.
- El **selector de fotos** entrega únicamente la imagen elegida: no hay
  acceso al resto del almacenamiento, así que no hace falta
  `READ_MEDIA_IMAGES`.

No es solo higiene de seguridad. **Cada diálogo de permiso es una barrera
más** —una decisión que tomar, un texto que leer, un botón que acertar—
justo para el usuario al que esta fase quiere servir. El manifiesto sigue
declarando exactamente dos permisos: `INTERNET` y `ACCESS_NETWORK_STATE`.

---

## 3. Backend: soporte multimodal

### 3.1 `ChatRequest` gana dos campos opcionales

```java
public record ChatRequest(
        @NotBlank @Size(max = 2000) String mensaje,
        List<ChatTurno> historial,
        @Size(max = 4_000_000) String imagenBase64,
        String imagenMimeType) { … }
```

Gson omite los campos `null`, así que **cuando no hay foto el JSON sale
idéntico al de la Fase 11**: no se rompe ningún cliente anterior.

El tope de 4 000 000 de caracteres (~3 MB) es una red de seguridad, no el
tamaño esperado: la app envía unos 200 KB. Sin límite, un cliente mal
implementado podría mandar una foto de 12 MP en crudo.

> **Limitación conocida.** La validación de Bean Validation se ejecuta
> DESPUÉS de que Jackson haya deserializado el cuerpo entero en memoria, y
> Spring Boot no impone por defecto ningún tope al tamaño de un cuerpo
> JSON. Para un TFG es asumible; en un despliegue real conviene un filtro
> que corte la petición por `Content-Length` antes de leerla.

### 3.2 `ImagenEntrante`: saneado compartido

Al aparecer un segundo punto de entrada de imágenes, los dos métodos de
saneado que vivían dentro de `VisionServiceImpl` se extraen a
`com.smartfridge.gemini.ImagenEntrante`. Duplicarlos habría significado
que una foto de la ESP32-CAM y una foto del chat se validasen con reglas
distintas — el tipo de divergencia que nadie detecta hasta que falla.

Hace tres cosas:

1. **Normaliza el MIME** al conjunto cerrado que acepta Gemini.
2. **Retira el prefijo de *data URL*** (`data:image/jpeg;base64,`) y los
   saltos de línea del Base64 MIME de 76 columnas. Son errores de cliente
   habitualísimos que Gemini rechaza con un 400 críptico; absorberlos
   cuesta cuatro líneas.
3. **Deduce el MIME** de los primeros caracteres del Base64 (`/9j/` →
   JPEG, `iVBORw0` → PNG, `UklGR` → WebP) cuando el cliente no lo declara,
   sin decodificar la cadena entera —lo que en una foto de varios MB no
   es un detalle menor.

### 3.3 La imagen se adjunta solo al turno actual

`AsistenteServiceImpl.turnoDelUsuario()` construye un
`GeminiMensaje.usuarioConImagen(...)` **únicamente para el turno en
curso**. Reenviar en cada petición todas las fotos de la conversación
multiplicaría el prompt y el coste sin aportar nada: si el usuario vuelve
a preguntar por la foto de hace tres turnos, lo que el modelo necesita es
lo que él mismo respondió entonces —que sí está en el historial como
texto—, no volver a mirar los píxeles.

### 3.4 Reglas nuevas del system prompt

Se añaden cuatro reglas para el caso de la fotografía:

- **11 · Identifica primero.** Si el producto coincide con el
  `INVENTARIO ACTUAL`, se enlaza con lo que ya se sabe de él. Si no, se
  trata como consulta externa: algo que el usuario está mirando en la
  tienda, no algo de lo que disponga.
- **12 · Precios: estimación, nunca dato.** El modelo no tiene acceso a
  precios en tiempo real. Puede dar una horquilla orientativa en euros y
  decir de qué depende, siempre declarando que es aproximada. **Nunca una
  cifra exacta como si la hubiera consultado.** Es la regla más
  importante de las cuatro: un precio inventado con aplomo es
  exactamente el tipo de error que un usuario no puede detectar.
- **13 · Nutrición general y declarada como tal**, remitiendo a la
  etiqueta para el dato exacto.
- **14 · Si la foto no se entiende, decirlo y pedir otra.** No adivinar.

---

## 4. Android: la foto, del sensor al JSON

`Imagenes.comprimirABase64()` reescala a 1024 px de lado mayor y comprime
a JPEG con calidad 80.

**Por qué en el móvil y no en el servidor.** Una foto de un teléfono
actual son 4-8 MB; en Base64 crece un 33 % más. Enviarla en crudo
significaría varios segundos de subida con datos móviles, un JSON que el
backend deserializa entero en memoria y otro tanto que Gemini debe
recibir — todo para analizar un producto que se reconoce perfectamente a
1024 px. El resultado ronda los 200 KB: **un factor de 30**, sin pérdida
apreciable para el modelo.

Tres detalles que importan:

1. **Decodificación en dos pasadas.** Primero solo las dimensiones
   (`inJustDecodeBounds`) para calcular un `inSampleSize`, y después la
   decodificación ya reducida. Decodificar la imagen completa para
   escalarla luego reservaría los 30-90 MB del bitmap a tamaño real: la
   causa más común de `OutOfMemoryError` al tratar fotos en Android.
2. **Orientación EXIF.** Casi todos los móviles guardan la foto en
   horizontal y anotan la rotación en los metadatos. Sin aplicarla, el
   modelo de visión recibe el producto tumbado, que es justo lo que peor
   reconoce. Se añade `androidx.exifinterface` para esto.
3. **Fuera del hilo principal.** El proceso son cientos de milisegundos.
   Corre en un `ExecutorService` propio del ViewModel, que se apaga en
   `onCleared()`.

### Si solo adjuntas la foto y pulsas enviar

El backend exige un mensaje no vacío. En vez de devolver un error, el
ViewModel pone la pregunta obvia —*"¿Qué es este producto?"*— en su
lugar. Adjuntar y enviar es el gesto más natural, y el único cómodo para
quien tiene dificultades motoras.

---

## 5. Diseño inclusivo en toda la aplicación

### 5.1 Área táctil

Todos los controles llegan a 48 dp, el umbral de Material Design y del
criterio **WCAG 2.2 · 2.5.8 (Target Size)**. El botón de enviar pasó de
40 dp a 48 dp; el micrófono es un FAB de 56 dp porque el dictado es la
vía preferente para quien no puede teclear cómodamente.

### 5.2 Agrupación semántica

Sin agrupar, TalkBack se detiene una vez por vista. En una fila de
inventario eso son **tres paradas** —"B", "Leche semidesnatada", "caduca
en 2 días"— y "B" leído suelto no significa nada.

Cada fila se convierte en **un solo punto de parada** con una frase
completa: el contenedor interno se marca
`importantForAccessibility="noHideDescendants"` y el adaptador compone la
descripción:

> *"Leche semidesnatada. Nutri-Score B. Caduca en 2 días."*

Aplicado a: filas de inventario, tarjetas de sensor, alertas, barras de
Nutri-Score de estadísticas y el indicador de "pensando".

### 5.3 El color no puede ser el único canal

El indicador de anomalía es un **icono rojo**. Quien no distingue el rojo
—en torno al 8 % de los hombres— o no ve la pantalla no percibiría la
alerta de ninguna forma. Es lo que prohíbe el criterio **WCAG 1.4.1 (Use
of Color)**.

Cada tarjeta de sensor declara su estado en palabras, **también cuando es
normal**:

> *"Temperatura interior: 4.2 °C. Estado normal."*
> *"Sensor de agua: Detectada. Atención: anomalía detectada."*

Anunciar la anomalía solo cuando existe obligaría al usuario a recordar
que la ausencia de frase significa "todo bien" — justo lo que no se puede
pedir a quien no ve la pantalla.

Lo mismo con las alertas: la palabra "Alerta" se antepone en la
descripción porque hoy ese carácter lo transmite el fondo rojo de la
tarjeta.

### 5.4 Etiquetas redactadas como acciones

`contentDescription` dice **qué va a pasar**, no cómo se llama el icono:
"Adjuntar una foto", no "clip". Y el interruptor de voz cambia de título
según su estado — "Leer las respuestas en voz alta" / "Dejar de leer en
voz alta"— porque quien navega a ciegas espera oír la acción, no el
estado.

### 5.5 Regiones vivas y foco

- La línea de estado es `accessibilityLiveRegion="polite"`: al cambiar a
  "Escuchando…", TalkBack lo anuncia solo. Sin esto, quien no ve la
  pantalla no sabría que el micrófono está abierto.
- Al retirar la foto adjunta, el foco vuelve al campo de texto: si no, se
  quedaría en un botón que acaba de desaparecer.
- Los cambios del modo voz se confirman con `announceForAccessibility`,
  para no depender de ver el icono.

### 5.6 Escalado de texto

Ningún tamaño de texto se fija en `dp`: todos salen de `textAppearance`,
en `sp`. Respetar el ajuste de "tamaño de fuente" del sistema es la
primera herramienta que usa una persona con baja visión, y un solo `dp`
la anula en esa pantalla. **Verificado: cero ocurrencias de
`android:textSize` en `dp` en los 17 layouts.**

### 5.7 El interruptor de voz no se esconde

Va en la barra superior con `showAsAction="always"`, no en el
desbordamiento de tres puntos. La función existe precisamente para quien
puede tener dificultad para leer; esconderla tras un menú la haría
inalcanzable justo para su destinatario.

### 5.8 Diálogo en vez de hoja inferior

El origen de la foto se elige en un `MaterialAlertDialogBuilder` con dos
entradas de lista: filas altas, texto que respeta el ajuste de fuente y
un recorrido de foco trivial. Una hoja inferior con dos iconos sería más
vistosa y bastante peor para el usuario al que va dirigida la pantalla.

---

## 6. Archivos

### Backend
| Archivo | Estado |
|---|---|
| `gemini/ImagenEntrante.java` | nuevo — saneado de imagen compartido |
| `dto/ChatRequest.java` | +`imagenBase64`, +`imagenMimeType` |
| `service/AsistenteServiceImpl.java` | turno multimodal + 4 reglas de prompt |
| `service/VisionServiceImpl.java` | usa `ImagenEntrante` (−17 líneas) |

### Android
| Archivo | Estado |
|---|---|
| `ui/util/Imagenes.java` | nuevo — reescalado, EXIF, Base64 |
| `ui/asistente/LectorDeVoz.java` | nuevo — TTS + detección de lector de pantalla |
| `res/menu/menu_asistente.xml` | nuevo — interruptor de voz |
| `res/drawable/ic_image_24`, `ic_volume_up_24`, `ic_volume_off_24`, `ic_close_24`, `bg_thumb` | nuevos |
| `ui/asistente/AsistenteViewModel.java` | adjunto, cola de locución, preferencia persistida |
| `ui/asistente/AsistenteFragment.java` | tres launchers, TTS, diálogo de origen |
| `ui/asistente/ChatAdapter.java` / `ChatMessage.java` | marca de foto adjunta |
| `res/layout/fragment_asistente.xml` | barra de entrada nueva, 48 dp, live region |
| `ui/dashboard/InventarioAdapter.java`, `ui/sensores/SensoresFragment.java`, `AlertaAdapter.java`, `StatsActivity.java` | descripciones agrupadas |
| `res/layout/item_inventario`, `item_alerta`, `item_chat_typing`, `item_stat_bar`, `fragment_sensores` | agrupación semántica |

**Dependencia nueva:** `androidx.exifinterface:exifinterface:1.3.7`.

**Recursos retirados:** `assistant_avatar_desc` (el avatar pasa a ser
decorativo), `assistant_speak_message`, `auth_ok_login`,
`common_more_options`, `sensors_subtitle`, `state_ok`,
`state_ok_container`.

---

## 7. Verificación

- **Sintaxis Java**: `javac 21` sobre los 15 fuentes modificados. Todos
  los errores registrados son de dependencias ausentes o cascadas suyas.
  **Cero errores de sintaxis.**
- **Contrato REST**: comprobación automática de que los cuatro DTO
  coinciden campo a campo entre backend y Android, incluidos los dos
  nuevos. **Coherente.**
- **Auditoría de accesibilidad automatizada** sobre los 17 layouts:
  ningún elemento interactivo sin `contentDescription`, ningún
  `ImageButton` por debajo de 48 dp, ningún `textSize` en `dp`.
- **Recursos huérfanos**: cero (con un verificador corregido — el
  anterior daba falsos negativos porque la propia definición del recurso
  contaba como uso).

### Pendiente de probar en dispositivo

1. **Activar TalkBack y recorrer la app entera** con el gesto de
   exploración. Es la única prueba que vale: ninguna verificación
   estática detecta que una frase agrupada suene mal.
2. Subir el tamaño de fuente del sistema al máximo y comprobar que nada
   se corta.
3. Probar el TTS en un dispositivo **sin voz española instalada**, para
   ver el camino de degradación.
4. Adjuntar una foto tomada en vertical y confirmar que Gemini la recibe
   derecha (la corrección EXIF).

---

## 8. Riesgos abiertos

1. **Secretos versionados** en `application.yml` (clave de Google y
   contraseña de Supabase). Sigue pendiente desde la Fase 11: rotarlas y
   dejar las variables sin valor por defecto.
2. **Endpoint de IA abierto**, ahora también con imágenes. Una petición
   de chat puede pesar 3 MB y consumir cuota de visión: el argumento para
   exigir autenticación y limitar la frecuencia es más fuerte que antes.
3. **Sin tope real de tamaño de cuerpo JSON** (ver 3.1).
4. **La voz no se reanuda tras una llamada telefónica**: no se solicita
   *audio focus*. Para una app en producción habría que pedirlo y pausar
   la locución cuando otro proceso lo reclame.
5. **Reproducción por mensaje suelto**: hoy la voz solo lee la respuesta
   nueva. Poder volver a escuchar una respuesta anterior —una acción de
   accesibilidad personalizada en cada burbuja— es la ampliación natural.
