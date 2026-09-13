# SmartFridge Backend — Fase 6 (autenticación de usuarios)

Sustituye a `ServerSmarfridge_entregable` (Servlets Java + Apache Tomcat +
MariaDB) del sistema legacy.

## Estructura de paquetes

```
com.smartfridge
├── SmartfridgeBackendApplication.java   # arranque (Tomcat embebido)
│
├── model/                    # [HECHO] Entidades JPA — capa de dominio
│   ├── Producto.java
│   ├── Usuario.java
│   ├── Inventario.java
│   ├── Sensor.java            # última lectura por sensor (estado actual)
│   ├── LecturaSensor.java     # histórico aditivo (serie temporal, para IA)
│   ├── Registro.java
│   ├── TipoSensor.java       (enum)
│   └── TipoRegistro.java     (enum)
│
├── repository/                 [HECHO] Spring Data JPA
│   ├── ProductoRepository.java
│   ├── UsuarioRepository.java         findByCorreo(String)
│   ├── SensorRepository.java          findByTipo(TipoSensor)
│   ├── LecturaSensorRepository.java   findByTipoSensorAndFechaBetweenOrderByFechaAsc(...)
│   ├── InventarioRepository.java      findByProducto_RfidTagOrderByFechaEntradaAsc / findFirstBy...
│   └── RegistroRepository.java        findByTipoRegistroOrderByFechaDesc(...)
│
├── service/                    [HECHO]
│   ├── SensorService.java / SensorServiceImpl.java
│   │       actualiza Sensor + guarda LecturaSensor (@Transactional)
│   ├── InventarioService.java / InventarioServiceImpl.java
│   │       anadirProducto() / retirarProducto() (FIFO, @Transactional)
│   ├── RegistroService.java / RegistroServiceImpl.java
│   │       registrarEntrada() / registrarSalida() / registrarAlerta()
│   └── UsuarioService.java / UsuarioServiceImpl.java
│           registrar() (hash BCrypt) / autenticar() (compara hash)
│
├── mqtt/                        [HECHO]
│   ├── MqttSubscriberService.java     conecta, se suscribe a frigorifico/#,
│   │                                   delega messageArrived() al router
│   ├── FrigorificoTopicRouter.java    Anti-Corruption Layer: los 7 topics
│   │                                   (temperature/humidity/water/door/
│   │                                   modo/rfid/anomalias) ya conectados
│   │                                   a su Service correspondiente
│   ├── ModoOperacion.java             enum INSERTAR/ELIMINAR
│   └── EstadoModoFrigorifico.java     estado en memoria (AtomicReference)
│                                       del modo actual, actualizado por
│                                       el topic "modo"
│
├── config/                      [HECHO]
│   ├── MqttProperties.java            record @ConfigurationProperties
│   ├── MqttConfig.java                Beans MqttClient + MqttConnectOptions
│   └── SecurityConfig.java            PasswordEncoder (BCrypt) + permitAll
│                                       TEMPORAL en /api/** (ver TODO en la clase)
│   (falta CorsConfig — se añadirá cuando el frontend web tenga origen definido)
│
├── controller/                 [HECHO] API REST para Android/Web
│   ├── ProductoController.java        GET/POST /api/productos
│   ├── InventarioController.java      GET /api/inventario (solo lectura, con nutriScore)
│   ├── SensorController.java          GET /api/sensores (solo lectura)
│   ├── RegistroController.java        GET /api/registros?tipo=... (solo lectura)
│   └── AuthController.java            POST /api/auth/login, POST /api/auth/registro
│
├── dto/                         [HECHO]
│   ├── ProductoRequest.java / ProductoResponse.java
│   ├── InventarioResponse.java        incluye nutriScore
│   ├── SensorResponse.java
│   ├── RegistroResponse.java
│   ├── LoginRequest.java / RegisterRequest.java / AuthResponse.java
│
└── exception/                   [HECHO]
    ├── GlobalExceptionHandler.java        @RestControllerAdvice
    ├── ProductoNoEncontradoException.java
    ├── InventarioVacioException.java
    ├── CredencialesInvalidasException.java
    └── CorreoYaRegistradoException.java
```

## Decisiones de esta iteración (para justificar en la memoria)

1. **`producto.rfid_tag` sigue siendo la PK natural** (no se sustituye por
   un `id` autonumérico): es el identificador físico que emite el lector
   RFID y evita un JOIN adicional al procesar eventos MQTT en tiempo real.
2. **`usuarios.pass` (texto plano) → `password_hash` (BCrypt)**, vía
   `PasswordEncoder` de Spring Security. Corrige una vulnerabilidad real
   del sistema legacy.
3. **Se elimina `usuarios.inventario` (int)**: no tenía una relación FK
   real implementada en ningún punto del código auditado. Ver comentario
   en `Usuario.java` para la alternativa recomendada (relación
   `@OneToMany` real) si se necesita soporte multi-usuario en el futuro.
4. **`inventario.producto_id` y `registro.id_producto` / `id_sensor`**
   pasan de ser columnas sueltas a relaciones `@ManyToOne` reales,
   delegando en Hibernate la integridad referencial.
5. **`sensores` se mantiene como tabla de "última lectura"**, fiel al
   esquema legacy, para responder rápido al estado actual del
   frigorífico.
6. **Se añade `lectura_sensor` como tabla de histórico (serie
   temporal)**: cada lectura MQTT genera una fila nueva (INSERT, nunca
   UPDATE), con índices sobre `(tipo_sensor, fecha)` pensados para las
   consultas de analítica/IA de la Fase 4 ("temperatura de los últimos
   7 días", detección de anomalías, etc.). Decisión tomada explícitamente
   para no bloquear esa fase más adelante.

## Decisiones de la Fase 3 (MQTT + lógica de negocio)

7. **`FrigorificoTopicRouter` como Anti-Corruption Layer**: es el único
   punto del backend que conoce el formato "de cable" del ESP32 (frases
   en español como "Agua detectada", números en texto plano). Traduce
   ese formato a llamadas de dominio tipadas antes de que lleguen a
   `SensorService`. Ver explicación completa en el chat.
8. **`SensorService.registrarLectura()` es `@Transactional`**: el
   upsert sobre `Sensor` (estado actual) y el `INSERT` sobre
   `LecturaSensor` (histórico) se confirman o se deshacen juntos.
   Ver explicación completa en el chat.
9. **La conexión MQTT se abre una sola vez, al arrancar la aplicación**
   (`@PostConstruct` en `MqttSubscriberService`), no en cada petición
   HTTP como hacía `DatabaseServlet.doPost()` en el sistema legacy —
   ese patrón legacy creaba una conexión MQTT nueva por cada llamada de
   la app Android sin cerrar las anteriores (fuga de conexiones).
10. **`rfid`, `modo` y `anomalias` se reconocen en el router pero no
    están conectados a ningún Service todavía**: quedan registrados en
    el log con la etiqueta `[Pendiente Fase 4]`. Requieren
    `InventarioService` (con la lógica FIFO de entrada/salida) y
    `RegistroService`, que se abordarán en la siguiente iteración.

## Decisiones de la Fase 4 (lógica de negocio + API REST)

11. **`EstadoModoFrigorifico` sustituye a `DatabaseServlet.modo`**:
    de un campo `public static String` sin garantías de visibilidad
    entre hilos, a un `AtomicReference<ModoOperacion>` en un
    `@Component`. Vive en el paquete `mqtt`, no en `service` ni
    `model`, porque es estado de la sesión de protocolo (interpretación
    de un flujo de eventos RFID), no un dato de dominio persistente.
12. **`InventarioService.anadirProducto/retirarProducto` son
    `@Transactional`** por el mismo motivo que `SensorServiceImpl`:
    cubren varias escrituras relacionadas (guardar/borrar la unidad de
    inventario + auditar en `RegistroService`) que deben confirmarse o
    deshacerse juntas.
13. **`InventarioController` es de solo lectura (solo GET)**: las
    altas y bajas de inventario solo ocurren a través del flujo MQTT
    (RFID + modo). No se expone un POST/DELETE directo en la API para
    no abrir una vía paralela que pudiera desincronizar inventario y
    registro sin pasar por `InventarioService`.
14. **`SecurityConfig` con `permitAll()` en `/api/**` es una decisión
    temporal, marcada explícitamente con un TODO en la clase**: se
    documenta como tal para que quede claro en la memoria que no es un
    descuido, sino una decisión de secuenciación (API REST navegable
    para desarrollar la app Android en paralelo, seguridad real en la
    fase final con JWT).

## Nota de seguridad

Durante esta iteración se compartió por error una contraseña real de
Supabase en el chat de desarrollo. Se recomienda rotarla desde el
dashboard de Supabase (Project Settings → Database → Reset database
password) y, en general, no pegar nunca `application.yml` con valores
reales en ningún sitio (chat, ticket, captura de pantalla): usar
siempre las variables de entorno ya soportadas por este archivo
(`DB_URL`, `DB_USER`, `DB_PASSWORD`).

## Decisiones de la Fase 6 (autenticación)

15. **`InventarioResponse` ahora incluye `nutriScore`** (parche
    pendiente desde la Fase 5, ya aplicado): la tabla del Dashboard de
    Android lo necesita y así se evita una llamada adicional a
    `/api/productos` solo para cruzar ese dato.
16. **`CredencialesInvalidasException` es la MISMA excepción tanto si
    el correo no existe como si la contraseña no coincide**: mismo
    mensaje genérico, mismo 401. Es una decisión de seguridad
    deliberada contra ataques de enumeración de usuarios (ver
    comentario en la propia clase) — vale la pena citarla en la
    memoria como ejemplo de decisión de seguridad no obvia a primera
    vista.
17. **`RegisterRequest` exige contraseña de mínimo 8 caracteres**
    (`@Size(min = 8)`), una validación que el sistema legacy no tenía.
    Pequeña mejora añadida sobre lo pedido explícitamente.
18. **`AuthResponse` nunca incluye `passwordHash`**, ni siquiera
    hasheada: es la misma lógica de "el DTO expone solo lo necesario"
    ya aplicada en el resto de la API, llevada aquí al caso más
    sensible.
19. **`UsuarioService.autenticar()` NO cambia contraseñas ni genera
    tokens todavía**: solo valida y devuelve el `Usuario`. La sesión
    en el móvil, tras un login exitoso, hoy no persiste nada más allá
    de navegar a `DashboardActivity` — no hay "recordar sesión" ni
    token que adjuntar a peticiones futuras. Es el mismo hueco que ya
    señala el TODO de `SecurityConfig` sobre JWT.

## Proveedor de base de datos: Supabase

`application.yml` está configurado para el **Session pooler** de
Supabase (puerto 5432, host `aws-0-<region>.pooler.supabase.com`,
usuario `postgres.<project-ref>`), no la conexión directa. Motivo: la
conexión directa de Supabase en el plan gratuito solo acepta IPv6, y la
mayoría de plataformas de despliegue gratuitas (Railway, Render) no
garantizan salida IPv6. El Session pooler mapea 1:1 cliente↔conexión
(como un Postgres normal) y es 100% compatible con el caché de
prepared statements de Hibernate sin configuración adicional — a
diferencia del Transaction pooler (puerto 6543), que exigiría añadir
`?prepareThreshold=0` a la URL JDBC y renunciar a esa caché. Para el
volumen de tráfico de un TFG, esa renuncia no compensa la complejidad
añadida; se documenta como posible mejora de escalabilidad futura.

**Antes de arrancar el proyecto**, sustituye en `application.yml` (o
mejor, como variables de entorno `DB_URL` / `DB_USER` / `DB_PASSWORD`):
- `<region>` por la región real del proyecto Supabase (visible en el
  connection string del dashboard).
- `CAMBIAR_PROJECT_REF` por el project-ref real (ej. `postgres.abcdefghijk`).
- La contraseña de la base de datos.
