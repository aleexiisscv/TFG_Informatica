# Fase 13 — Orquestación, seguridad y lógica de negocio

Cierre del backend: salida determinista en visión, disparador
cámara-puerta, motor de caducidades y JWT sobre los endpoints de IA.

---

## 1. Determinismo en visión: de prompt a gramática

### El problema, con nombre y apellidos

Hasta ahora `VisionServiceImpl` pedía **por favor**, en el prompt, que
el modelo respondiera en `snake_case`. Funcionaba casi siempre, y ese
"casi" era el problema: ante la misma foto podía devolver
`brick_leche`, `leche_entera_asturiana` o simplemente `leche`. Solo el
primero existía en la tabla `producto`, así que los otros dos
terminaban en un `ProductoNoEncontradoException` → 404.

Un fallo **silencioso y no reproducible**, que es la peor clase de
fallo: la foto era correcta, el modelo respondió, el servidor devolvió
un error, y repetir la operación podía funcionar.

### La corrección: un esquema, no una súplica

La petición lleva ahora un `responseSchema` de tipo enumerado
construido con los `rfid_tag` que existen **realmente** en el catálogo:

```json
"generationConfig": {
  "temperature": 0.0,
  "responseMimeType": "text/x.enum",
  "responseSchema": {
    "type": "STRING",
    "enum": ["brick_leche_entera", "huevos_docena", "…", "DESCONOCIDO"]
  }
}
```

**La diferencia es de naturaleza, no de grado:**

| | Prompt | Esquema |
|---|---|---|
| Qué es | Una petición en lenguaje natural | Una restricción del decodificador |
| Puede incumplirse | Sí | **No** |
| Cuándo falla | Cuanto más ambigua es la imagen | — |
| Espacio de respuestas | Cualquier cadena | Una de estas N |

El modelo no genera texto y luego se comprueba: el motor de muestreo
**no puede emitir** un token que saque la salida de la gramática del
enum. Es la misma idea que un tipo de dato frente a un comentario que
dice "aquí va un número".

### Lo que el esquema NO arregla, dicho en voz alta

El modelo sigue pudiendo elegir el valor **equivocado** de la lista —
confundir dos marcas de leche. Lo que desaparece es la categoría
"identificador inventado".

Y esa reducción es la que importa: se pasa de un fallo **abierto**
(cadena arbitraria → 404 críptico) a uno **cerrado** (clasificación
errónea entre opciones válidas). El segundo es medible con una matriz
de confusión, reproducible con la misma foto y corregible con mejores
fotos o mejores nombres de producto. El primero no.

En términos de arquitectura: el contrato entre visión e inventario deja
de ser una convención de texto y pasa a ser, en la práctica, una
**clave foránea**. Lo que devuelve el servicio existe en el catálogo
por construcción, no por suerte.

### Tres detalles de implementación

1. **`DESCONOCIDO` sigue en el enum.** Sin esa escapatoria, el modelo
   estaría obligado a elegir un producto ante una foto de una pared, y
   el sistema daría de alta algo que nadie ha metido en el frigorífico.
   Un "no lo sé" explícito es información; una respuesta forzada es
   basura con formato correcto.
2. **El prompt cambia de trabajo.** Ya no enseña un formato —de eso se
   encarga el esquema— sino que aporta lo único que el esquema no
   puede dar: el **significado** de cada identificador
   (`brick_leche_entera → Leche entera 1 L`). La tarea pasa de
   "descifrar una cadena" a "emparejar lo que veo con una lista".
3. **Catálogo vacío → no se llama a Gemini.** El enum solo podría
   contener `DESCONOCIDO`, así que la respuesta se conoce de antemano y
   la llamada sería puro gasto de cuota.

---

## 2. Orquestación cámara-puerta — **entregada apagada**

### Estado

Implementada, compilable y probada… y **desactivada por defecto**
(`smartfridge.camara.disparo-automatico: false`). Con la configuración
de serie, `CamaraConfig` y `CapturaAutomaticaService` ni siquiera se
instancian: el sistema se comporta exactamente igual que antes de esta
fase, con la ESP32-CAM tomando su fotografía al arrancar y subiéndola
ella misma.

**Motivo:** el sensor magnético de puerta produce falsos contactos. Un
disparador conectado a un sensor que rebota dispararía capturas en
ráfaga, y cada captura es una inferencia multimodal de pago.

### Por qué un interruptor y no código comentado

Se pidió dejarlo comentado. Se ha hecho con un flag de configuración,
y conviene justificar el cambio: **el código comentado no compila, no
se revisa y se pudre en silencio**. Al descomentarlo meses después casi
nunca funciona a la primera, porque el resto del proyecto ha seguido
avanzando sin él.

Un flag mantiene la ruta viva —compila, entra en el análisis estático,
se puede probar poniendo la propiedad a `true`— sin ejecutarse nunca.
Activarlo es cambiar una línea de `application.yml`, sin recompilar. Y
el efecto sobre el sistema en producción es idéntico al de comentarlo:
cero.

### El servidor descarga, la cámara no sube

Se descartó la variante "el backend ordena disparar y la placa sube el
JPEG" por una razón que se ve al cruzarla con el objetivo 4: ese
endpoint queda cerrado con JWT en esta misma fase, así que habría que
dejarle una puerta abierta permanente.

Descargando la imagen (`GET /capture` → `image/jpeg`), la comunicación
es **saliente**: la placa no necesita credenciales del backend, no
tiene que conocer su IP, y el endpoint de visión puede cerrarse de
verdad.

> **Requisito de firmware.** El sketch actual de la ESP32-CAM es un
> cliente HTTP, no un servidor: sube por multipart y no atiende
> peticiones. Para activar esta ruta hay que añadirle un servidor HTTP
> mínimo que responda a `/capture` con el JPEG. Mientras el disparo
> siga apagado, no hace falta tocar nada.

### Detalles que no son adorno

- **`@Async`.** La captura NO puede correr en el hilo que entrega los
  mensajes MQTT: ese hilo es único, y bloquearlo diez segundos
  esperando a una placa lenta dejaría sin procesar las lecturas de
  temperatura que lleguen mientras tanto.
- **Evento de aplicación, no llamada directa.** El
  `FrigorificoTopicRouter` es una capa anticorrupción; si llamara al
  servicio de captura pasaría a saber que existe una cámara. Publica un
  hecho del dominio (`PuertaCerradaEvent`) y se desentiende.
- **Detección de transición.** El ESP32 republica el estado en cada
  vuelta de su bucle, no solo cuando cambia. El router recuerda el
  último estado y solo anuncia el cierre en la transición
  abierta → cerrada.
- **Antirrebote con CAS.** Un intervalo mínimo de 15 s convierte una
  ráfaga de rebotes en una sola captura. Se usa `compareAndSet` y no
  una comprobación normal porque los eventos llegan en hilos del pool
  de `@Async` y pueden solaparse: con CAS, de dos capturas simultáneas
  solo una gana la carrera.

---

## 3. Motor de caducidades

Tarea `@Scheduled` diaria que convierte una fecha guardada en base de
datos en un aviso que el usuario ve.

Hasta ahora `fecha_caducidad` solo se pintaba si el usuario abría el
inventario. Es decir: **el sistema sabía que la leche caducaba mañana y
no se lo decía a nadie.** Este proceso cierra esa brecha — es la
diferencia entre almacenar datos y prestar un servicio.

### Decisiones

- **08:00, no cada hora.** La caducidad se mide en días: comprobarla
  más a menudo no adelanta ninguna información, solo multiplica
  escrituras. Y a primera hora porque es cuando el aviso todavía sirve
  para decidir el desayuno o la compra; a las once de la noche llega
  tarde.
- **Zona horaria explícita** (`zone = "Europe/Madrid"`). Sin ella el
  cron usaría la del sistema, y un despliegue en la nube casi siempre
  corre en UTC: las "8:00" serían las 10:00 en horario de verano.
- **Agrupación por producto.** Seis yogures del mismo lote son **un**
  aviso, no seis: el usuario necesita saber que "los yogures caducan",
  no la misma frase repetida media docena de veces.
- **Sin cota inferior.** Se incluyen los ya caducados: un producto que
  venció ayer y sigue dentro es *más* urgente, no menos.
- **Idempotencia por día.** `existsByTipoRegistroAndProducto_RfidTagAndFechaGreaterThanEqual`
  evita que un reinicio a media mañana duplique todas las alertas.
  Repetir el recordatorio cada día es intencionado —la urgencia
  aumenta—; repetirlo tres veces la misma mañana solo es ruido.
- **Método público en la interfaz.** Una tarea programada que solo se
  puede probar esperando 24 horas es una tarea que nadie prueba. Se
  expone `revisarCaducidades()` para invocarla desde un test o un
  endpoint de administración.

### Cómo se modela la alerta

Se guarda como `TipoRegistro.ALERTA` —igual que las de sensor— pero con
`producto` relleno y `sensor` nulo. Rompe la exclusión mutua que
documenta la entidad `Registro`, y conviene ser explícito:

La alternativa era crear un `TipoRegistro.CADUCIDAD`, que habría dejado
estas alertas **fuera** del panel de notificaciones de la app —que
consulta `/api/registros?tipo=ALERTA`— hasta actualizar también el
cliente. Reutilizar ALERTA hace que aparezcan desde el primer día donde
el usuario ya mira. `RegistroResponse` ya expone ambos campos como
opcionales, así que la API lo soporta sin cambios.

Los días restantes viajan en `medicion` (negativo si ya caducó), que es
el campo que la app ya sabe leer de una alerta.

---

## 4. Seguridad: JWT sobre los endpoints de IA

### Qué se cierra y por qué esos

`/api/vision/**` y `/api/asistente/**`. El criterio no es "cerrar lo
que parezca sensible" sino **cerrar primero lo que un tercero puede
convertir en una factura**: los endpoints de datos exponen el
inventario de una nevera; los de IA exponen una tarjeta de crédito.

`/api/inventario`, `/api/sensores`, `/api/registros` y
`/api/productos` siguen abiertos. Es una decisión consciente de alcance
—cerrarlos obliga a que la app envíe el token en todas sus pantallas y
a que cualquier prueba con Postman lo incluya— y está marcada en el
código como el último paso natural.

### Resource Server, no una librería suelta

Se usa `spring-boot-starter-oauth2-resource-server` en lugar de jjwt:
la validación (firma, expiración, emisor) la hace un componente
mantenido por el equipo de Spring e integrado en la cadena de filtros,
en vez de un filtro propio escrito a mano. **Menos código nuestro en la
ruta crítica de seguridad es exactamente lo que se quiere en la ruta
crítica de seguridad.**

HS256 y no RS256 porque quien firma y quien verifica son el mismo
servicio: un par de claves solo compensa cuando un tercero debe validar
tokens sin poder emitirlos.

### La clave de firma no tiene valor por defecto

Una clave versionada en Git es una clave **pública**. Ante su ausencia
caben dos salidas y ninguna es obvia: fallar al arrancar (correcto en
producción, desastroso a mitad de una demostración por una variable de
entorno olvidada) o generar una aleatoria en memoria (arranca siempre,
los tokens no sobreviven al reinicio).

Se elige la segunda **con un aviso muy visible en el log**: el fallo
resultante —"me ha caducado la sesión al reiniciar"— es evidente y se
diagnostica solo, mientras que una clave compartida y filtrada no da
ningún síntoma hasta que alguien la aprovecha.

Una clave **corta**, en cambio, sí aborta el arranque: no es un
descuido de configuración, es una firma débil, y arrancar daría una
falsa sensación de seguridad.

### El algoritmo hay que declararlo en los dos extremos

Primer fallo real en ejecución: `JwtEncodingException: Failed to select
a JWK signing key` al iniciar sesión. La causa no es la clave sino el
**algoritmo por defecto** del codificador.

`NimbusJwtEncoder`, si la cabecera del token no dice lo contrario,
asume **RS256**. A continuación pide a su `JWKSource` una clave capaz
de firmar con RS256 — y el `ImmutableSecret` que se le pasó solo
contiene una clave simétrica (`OctetSequenceKey`). El selector no
encuentra ninguna candidata y aborta.

El decodificador sí fijaba `MacAlgorithm.HS256` explícitamente; el
codificador no. Un descuido asimétrico:

```java
JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
jwtEncoder.encode(JwtEncoderParameters.from(cabecera, claims));
```

La lección va más allá del error concreto: en criptografía, **el
algoritmo forma parte del contrato y no debe quedar nunca en manos de
un valor por defecto**. La misma regla que obliga a fijar
`macAlgorithm` en el decodificador —para no aceptar el algoritmo que
proponga quien envía el token— obliga a declararlo al firmar. Aquí el
síntoma fue un 500 inmediato y ruidoso, que es la forma benigna de
equivocarse; la forma maligna es un sistema que arranca y firma con
algo distinto de lo que se creía.

### La ESP32-CAM no es una persona

El JWT identifica a alguien que ha iniciado sesión. La cámara no tiene
teclado, no puede renovar un token caducado ni volver a escribir una
contraseña. Meterle credenciales de usuario en el firmware sería *peor*
que no cerrar el endpoint: acabarían en el repositorio y servirían para
todo lo demás.

La solución estándar para un dispositivo desatendido es un secreto
propio con **alcance mínimo**: la cabecera `X-Device-Key` solo abre
`/api/vision/**`. Si la placa se pierde, se rota una propiedad sin
tocar ninguna cuenta de usuario.

Dos detalles:

- **Comparación en tiempo constante** (`MessageDigest.isEqual`).
  `String.equals` corta en el primer carácter distinto, así que el
  tiempo de respuesta filtra cuántos se acertaron.
- **El filtro no es un `@Component`.** Spring Boot registra
  automáticamente en la cadena del servlet cualquier bean de tipo
  `Filter`, con lo que se ejecutaría **dos veces**: una dentro de la
  cadena de Spring Security y otra fuera, antes de que exista contexto
  de seguridad. Se instancia a mano en `SecurityConfig`.

### Android: la trampa del token en el login

El interceptor de OkHttp adjunta `Authorization: Bearer` a todas las
peticiones **salvo a `/api/auth/**`**. No es una optimización: con
Spring Security como Resource Server, un `Bearer` inválido o caducado
provoca un 401 *antes* de llegar al controlador, aunque la ruta sea
pública. Enviar el token viejo al login impediría volver a entrar —
justo cuando el usuario más lo necesita. Es un fallo sutil y
desconcertante de depurar.

Ante un 401 el interceptor borra la sesión local, el asistente muestra
un mensaje específico ("tu sesión ha caducado") y ofrece un Snackbar
con acción para volver al login. No se navega desde el interceptor:
una capa de red no debe conocer la interfaz.

---

## 5. Pantalla de sensores: se retira la promesa

El panel decía *"Cuando el modelo de analítica esté entrenado, aquí
aparecerán las predicciones…"*. Se ha sustituido, no eliminado.

**Criterio:** una interfaz que promete funciones futuras es la misma
clase de problema que el interruptor RFID que se retiró en la fase de
rediseño — enseña al usuario que la app dice cosas que no son. Pero
ahora sí hay algo real que contar, así que el hueco no se queda vacío:

> **Vigilancia automática** — El servidor revisa cada día el inventario
> y avisa de lo que está a punto de caducar. Los sensores marcan una
> anomalía en su tarjeta cuando el valor se sale de rango, y las
> incidencias quedan registradas abajo.

Todo eso es verdad a partir de esta fase. Los `anomalyBadge` de las
tarjetas siguen ahí como punto de extensión para el detector de
anomalías sobre serie temporal, pero ya no se anuncian como una
promesa.

Además, `AlertaAdapter` aprende a pintar el nuevo tipo de alerta: si el
registro trae `nombreProducto` es una caducidad (icono de reloj,
*"Leche caduca en 2 días"*); si trae `sensorTipo`, una alerta de sensor
como hasta ahora.

---

## 6. Archivos

### Backend — nuevos
```
config/CamaraProperties.java          Configuración de la ESP32-CAM
config/CamaraConfig.java              RestClient (solo si el disparo está activo)
config/ProgramacionConfig.java        @EnableScheduling + @EnableAsync
config/SeguridadProperties.java       smartfridge.seguridad.*
config/JwtConfig.java                 SecretKey + JwtEncoder + JwtDecoder
config/DeviceKeyAuthenticationFilter  Autenticación de dispositivo
evento/PuertaCerradaEvent.java        Evento de dominio
service/TokenService.java             Emisión de JWT
service/CapturaAutomaticaService.java Orquestación cámara-puerta
```

### Backend — modificados
```
gemini/GeminiOpciones.java       + Esquema (responseSchema)
gemini/GeminiClient.java         serializa responseMimeType/responseSchema
service/VisionServiceImpl.java   enum desde el catálogo real
service/InventarioService(Impl)  + revisarCaducidades() @Scheduled
service/RegistroService(Impl)    + registrarAlertaCaducidad()
repository/InventarioRepository  + consulta de próximos a caducar
repository/RegistroRepository    + comprobación de idempotencia
mqtt/FrigorificoTopicRouter      detección de transición + publicación del evento
config/SecurityConfig.java       stateless + JWT + rutas cerradas
controller/AuthController.java   devuelve token
dto/AuthResponse.java            + token, tipoToken, expiraEnSegundos
pom.xml                          + spring-boot-starter-oauth2-resource-server
application.yml                  + seguridad, caducidades, camara
```

### Android
```
api/SesionUsuario.java           NUEVO — almacén del token
api/RetrofitClient.java          interceptor Authorization + 401
api/dto/AuthResponse.java        + campos de token
SmartFridgeApp.java              inicializa la sesión
LoginActivity / RegisterActivity guardan el token
ui/asistente/AsistenteViewModel  distingue el 401
ui/asistente/AsistenteFragment   Snackbar con acción "Iniciar sesión"
ui/sensores/AlertaAdapter        alertas de caducidad
res/drawable/ic_schedule_24.xml  NUEVO
res/values/strings.xml           panel de vigilancia + textos de caducidad
```

---

## 7. Configuración nueva

```yaml
smartfridge:
  seguridad:
    jwt-secret: ${JWT_SECRET:}            # openssl rand -base64 48
    jwt-expiracion-minutos: 720
    clave-dispositivo: ${DEVICE_KEY:}     # cabecera X-Device-Key
  caducidades:
    cron: "0 0 8 * * *"                   # pruebas: "0 */2 * * * *"
    dias-margen: 2
  camara:
    disparo-automatico: false             # ← APAGADO a propósito
    base-url: ${CAMARA_BASE_URL:}
    ruta-captura: /capture
    intervalo-minimo-ms: 15000
```

---

## 8. Verificación

- **Sintaxis Java**: `javac 21` sobre los 22 fuentes de backend y los 11
  de Android modificados. Todos los errores son de dependencias
  ausentes o cascadas suyas (incluida la de los getters de Lombok).
  **Cero errores reales.**
- **`application.yml`**: parsea; las tres secciones nuevas se leen.
- **Contrato REST**: los **ocho** DTO coinciden campo a campo entre
  backend y Android, incluido `AuthResponse` con los tres campos
  nuevos.
- **Recursos Android**: ningún `R.*` roto, ningún recurso huérfano,
  IDs coherentes con los layouts.

### Pendiente de probar en ejecución

1. `mvn spring-boot:run` con `JWT_SECRET` definido. Sin él arranca
   igual, pero con aviso.
2. `POST /api/auth/login` → comprobar que devuelve `token`.
3. `POST /api/asistente/chat` **sin** cabecera → debe dar **401**.
   Con `Authorization: Bearer <token>` → 200.
4. **Motor de caducidades**: poner `cron: "0 */2 * * * *"`, dar de alta
   un producto con `plazo_caducidad` de 1 día y comprobar que en dos
   minutos aparece la alerta en la app.
5. **Salida estructurada**: fotografiar el mismo producto tres veces y
   confirmar que devuelve el mismo identificador exacto. Y fotografiar
   algo que NO esté en el catálogo: debe responder `DESCONOCIDO`, no un
   producto parecido.
6. La ESP32-CAM necesita ahora enviar `X-Device-Key` si se configura
   `DEVICE_KEY`; si se deja vacío, `/api/vision/analizar` solo acepta
   JWT y la placa dejará de poder subir.

---

## 9. Deuda que queda abierta

1. **Secretos versionados** en `application.yml`: la clave de Gemini y
   la contraseña de Supabase siguen ahí desde fases anteriores. Las
   nuevas (`JWT_SECRET`, `DEVICE_KEY`) sí quedan sin valor por defecto.
   Rotar las dos antiguas es el trabajo pendiente más urgente.
2. **Resto de la API abierto.** Cambiar `permitAll()` por
   `authenticated()` en la línea marcada de `SecurityConfig`, y
   comprobar que la app envía token en todas las pantallas.
3. **Sin refresh token.** Cuando el JWT caduca (12 h) hay que volver a
   iniciar sesión. Un refresh token es la mejora natural.
4. **Token en `SharedPreferences` sin cifrar.** En un dispositivo sin
   rootear ninguna otra app puede leerlo, pero
   `EncryptedSharedPreferences` lo protegería también frente a una
   extracción física.
5. **Sin límite de peticiones** en los endpoints de IA. El JWT impide
   el acceso anónimo, pero un usuario autenticado sigue pudiendo
   encadenar consultas sin tope.
6. **La ESP32-CAM necesita un servidor HTTP** para que el disparo
   automático pueda activarse.
