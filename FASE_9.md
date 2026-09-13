# Fase de rediseño UI/UX — SmartFridge Android
 
Documento de decisiones de la fase de rediseño de la capa de Aplicación
(Android). Escrito para poder trasladarse casi literalmente al capítulo
de diseño de la memoria del TFG.
 
Punto de partida: rama `main`, commit `d897dae` ("SERVIDOR Y APP
OPERATIVAS, PRE-DISEÑO"). La capa de red (Retrofit + DTOs contra el
backend Spring Boot) **no se ha tocado**: esta fase actúa solo sobre
presentación, navegación y limpieza de código muerto.
 
---
 
## 1. Auditoría previa: qué se encontró
 
Antes de rediseñar nada se auditó el módulo. Los hallazgos, ordenados de
mayor a menor gravedad:
 
| # | Hallazgo | Consecuencia real |
|---|----------|-------------------|
| 1 | `StatsActivity` generaba el Nutri-Score con `Math.random()` | Las estadísticas mostradas eran **ficticias** |
| 2 | `StatsActivity` llamaba a `thread.join()` en el hilo principal | ANR garantizado ("Application Not Responding") |
| 3 | `StatsActivity` apuntaba a un servlet legacy con IP incrustada (`192.168.116.180:8080`) | La pantalla no podía funcionar: ese servidor ya no existe |
| 4 | Fechas parseadas con `"MMM d, yyyy, h:mm:ss a"` en inglés | El backend emite ISO-8601: toda fecha lanzaba `ParseException` silenciosa |
| 5 | Permiso `READ_PHONE_STATE` declarado y no usado | Permiso **peligroso** sin contrapartida; motivo de rechazo en Play |
| 6 | Inventario pintado con `TableLayout` construida a mano | Sin reciclado de vistas; redibujado total en cada refresco |
| 7 | Sin estado vacío ni estado de error | Un backend caído y un frigorífico vacío se veían **idénticos** |
| 8 | Sondeo cada 2 s detenido solo en `onDestroy` | ~1.800 peticiones/hora, también con la pantalla apagada |
| 9 | `Switch` de modo RFID no conectado a nada | Control que miente al usuario |
| 10 | `CheckBox` "Male"/"Female" en registro | Dato personal pedido y nunca usado (minimización de datos, RGPD 5.1.c) |
| 11 | `activity_register.xml` usaba `@color/material_dynamic_secondary70` | Recurso solo existente en API 31+, con `minSdk = 24` |
| 12 | `RegisterActivity` usaba R.id "asumidos" | Código y layout podían divergir sin error de compilación |
| 13 | Todos los textos incrustados en los layouts | Aviso `HardcodedText` de Lint; imposible traducir |
| 14 | Márgenes arbitrarios (114dp, 286dp, 137dp…) del editor visual | Layouts que solo cuadran en la pantalla donde se dibujaron |
| 15 | `User` / `UserManager` con contraseñas en memoria y en claro | Código muerto peligroso (ya sustituido por BCrypt en el backend) |
 
---
 
## 2. Decisiones de arquitectura visual
 
### 2.1 Navegación: una Activity, tres Fragments
 
El sistema heredado saltaba entre Activities con
`startActivity() + finish()`. Se sustituye por **una sola
`MainShellActivity`** que aloja un `NavHostFragment` y una
`BottomNavigationView` persistente.
 
**Por qué:** con Activities independientes cada salto destruía y
reconstruía toda la jerarquía de vistas, cada pantalla tenía que
repintar su propia barra y el botón "atrás" del sistema devolvía a
pantallas ya cerradas. Con un `NavHost`, las tres secciones comparten
Activity y ciclo de vida, la barra no parpadea y el back stack lo
gestiona el Navigation Component.
 
Los `android:id` de `menu_bottom_nav.xml` coinciden **exactamente** con
los de los destinos de `nav_graph.xml`: ese es el contrato que permite
que `NavigationUI.setupWithNavController()` enlace barra y grafo sin una
sola línea de listener manual.
 
Las pantallas secundarias (login, registro, alta de producto,
estadísticas, escaneo) **siguen siendo Activities a propósito**: son
flujos de entrada/salida que no deben mostrar la barra inferior.
 
### 2.2 Color: Dynamic Color con paleta de respaldo
 
`SmartFridgeApp` (nueva clase `Application`) llama a
`DynamicColors.applyToActivitiesIfAvailable(this)`. En Android 12+ la
app adopta la paleta derivada del fondo de pantalla del usuario; en
Android 11 o anterior cae a la paleta propia definida en
`values/colors.xml`.
 
**Por qué en `Application` y no en cada Activity:** hacerlo por Activity
obliga a recordarlo en cada pantalla nueva —fallo por omisión
silencioso, visible solo como una pantalla "descolorida"— y hace que
cada Activity conozca de temas además de su propia lógica.
 
**Cómo funciona sin tocar layouts:** ningún layout referencia
`@color/…`; todos usan atributos de tema (`?attr/colorPrimary`,
`?attr/colorSurfaceContainer`…). Por eso la paleta se puede reescribir
en caliente.
 
**Excepción deliberada:** los colores de **Nutri-Score** y de **estado
de alarma** (verde/ámbar/rojo) NO son roles del tema. El Nutri-Score es
un código normativo: si el fondo de pantalla del usuario tiñera la "A"
de morado, dejaría de ser información nutricional. Semántica antes que
estética.
 
### 2.3 Jerarquía por tono, no por sombra
 
Las tarjetas usan `Widget.Material3.CardView.Filled` con
`cardElevation = 0dp` y distintos niveles de
`colorSurfaceContainer*`. Es el criterio de M3: la profundidad se
comunica con el tono de la superficie. Las tarjetas **con contorno** se
reservan para bloques de IA, de modo que el usuario distinga de un
vistazo el dato *medido* (relleno) del dato *inferido por un modelo*
(contorno).
 
---
 
## 3. Ganchos reservados a la Inteligencia Artificial
 
El objetivo era que integrar IA más adelante **no obligue a rediseñar**.
Puntos de extensión ya presentes:
 
| Gancho | Dónde | Estado |
|--------|-------|--------|
| `aiInsightRow` / `aiInsightText` | `item_inventario.xml` | Fila oculta reservada a predicciones de consumo por producto |
| `tempAnomaly`, `humAnomaly`, `waterAnomaly`, `doorAnomaly` | `fragment_sensores.xml` | Badge de anomalía por sensor; `SensoresFragment.marcarAnomalia()` es el único punto de entrada |
| `aiPanel` | `fragment_sensores.xml` | Tarjeta con contorno para el análisis predictivo sobre la serie temporal (`lectura_sensor`) |
| `AsistenteFragment.responder(String)` | Asistente | Único método a sustituir por la llamada al modelo o al endpoint |
| `ScanProductActivity.clasificar(Bitmap)` | Escaneo | Único método a sustituir por la inferencia (TFLite/ML Kit en dispositivo, o subida al backend) |
 
En el asistente, la firma `String -> String` es deliberadamente simple;
cuando la respuesta sea asíncrona basta cambiarla por un callback que
llame a `anadir(ChatMessage)`. La pantalla no sabe —ni debe saber—
quién genera la respuesta (inversión de dependencias).
 
---
 
## 4. Visión artificial y voz: sin permisos peligrosos
 
- **Cámara**: se usa `ActivityResultContracts.TakePicturePreview`, que
  delega la captura en la app de cámara del sistema. La app **no
  declara ni solicita `CAMERA`** porque nunca abre el sensor.
- **Dictado**: se usa `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`
  lanzado como Intent, no la API `SpeechRecognizer` en proceso. Graba el
  reconocedor del sistema, así que **no hace falta `RECORD_AUDIO`**.
Ambos permisos solo serán necesarios el día que se integre vista previa
en vivo con CameraX o reconocimiento en proceso — y entonces estarán
justificados. Principio de mínimo privilegio.
 
Se retiran además `READ_PHONE_STATE` (peligroso, sin uso), `WAKE_LOCK`
(sin uso) y `POST_NOTIFICATIONS` (no hay notificaciones implementadas).
 
La cámara del móvil actúa como **plan de contingencia** de la cámara
Edge AI del propio frigorífico.
 
---
 
## 5. Reescritura de `StatsActivity`
 
Era el mayor punto de fallo del módulo (hallazgos 1–4). Ahora:
 
1. Dos llamadas Retrofit **asíncronas** encadenadas en callbacks; ninguna
   bloquea el hilo principal.
2. `GET /api/productos` construye el mapa `nombre -> Nutri-Score`;
   `GET /api/registros` aporta movimientos y alertas. Datos **reales**.
3. Selector de fecha con `MaterialDatePicker`; ventana por defecto de 30
   días para no arrancar en blanco.
4. La distribución de Nutri-Score se representa con **barras
   proporcionales** en lugar de texto "A: 42 %": una comparación entre
   magnitudes se resuelve visualmente mucho más rápido. El porcentaje
   exacto se conserva al final de cada barra, también para lectores de
   pantalla.
> **Deuda técnica reconocida.** El cruce producto↔Nutri-Score debería
> hacerlo el servidor. `RegistroResponse` transporta `nombreProducto`
> pero no `nutriScore`, así que el móvil descarga el catálogo entero
> para completar el dato — el mismo anti-patrón que ya se eliminó en el
> inventario cuando `InventarioResponse` pasó a devolver el JOIN
> resuelto. **Añadir `nutriScore` a `RegistroResponse` eliminaría una
> petición completa.** Candidato claro para la siguiente iteración del
> backend.
 
---
 
## 6. Ciclo de vida y consumo de red
 
| Antes | Ahora | Motivo |
|-------|-------|--------|
| Sondeo cada 2 s | Alertas cada 15 s (shell), sensores cada 5 s (solo con la pestaña visible) | 2 s ≈ 1.800 peticiones/hora sin justificación: el dato cambia como mucho cada minutos |
| `handler.removeCallbacks()` en `onDestroy` | En `onPause` | La app dejaba de consumir red solo al cerrarse, no al pasar a segundo plano |
| Sin cancelación de llamadas | `Call.cancel()` en `onDestroyView`/`onPause` + comprobación `getView() != null` | Una respuesta que llega tras destruirse la vista provocaba NPE intermitente |
| Sondeo ciego del inventario | `SwipeRefreshLayout` (pull to refresh) + recarga en `onResume` | El inventario cambia cuando el usuario mete o saca algo |
 
---
 
## 7. Ficheros
 
### Nuevos (Java)
```
SmartFridgeApp.java              Application: activa Dynamic Color
MainShellActivity.java           NavHost + BottomNav + badge de alertas
ScanProductActivity.java         Visión artificial (UI lista)
ui/dashboard/DashboardFragment.java
ui/dashboard/InventarioAdapter.java
ui/sensores/SensoresFragment.java
ui/sensores/AlertaAdapter.java
ui/asistente/AsistenteFragment.java
ui/asistente/ChatAdapter.java
ui/asistente/ChatMessage.java
ui/util/Fechas.java              Parseo/formato ISO-8601 y caducidades
ui/util/NutriScoreUi.java        Nutri-Score -> color (código normativo)
```
 
### Reescritos
`LoadingActivity`, `LoginActivity`, `RegisterActivity`,
`CreateProductActivity`, `StatsActivity`, `AndroidManifest.xml`,
`app/build.gradle.kts`, `gradle/libs.versions.toml`,
`values/colors.xml`, `values/themes.xml`, `values-night/themes.xml`,
`values/strings.xml` (110 cadenas extraídas).
 
### Eliminados
```
MainActivity.java            Pantalla de bienvenida redundante tras el splash
DashboardActivity.java       -> DashboardFragment
SensorActivity.java          -> SensoresFragment
ServerConnectionThread.java  Hilo HTTP contra servlets legacy
User.java / UserManager.java Autenticación en memoria, en texto plano
activity_main.xml, activity_principal.xml,
activity_dashboard.xml, activity_sensors.xml
anonimo.jpg, circle_red.png, ic_bell.png, smartfridgelogo.png
                             Raster sustituido por vectores
```
 
---
 
## 8. Dependencias añadidas
 
| Dependencia | Versión | Para qué |
|-------------|---------|----------|
| `androidx.navigation:navigation-fragment` / `-ui` | 2.7.7 | BottomNav + NavHost |
| `androidx.fragment:fragment` | 1.8.5 | Fragments |
| `androidx.recyclerview:recyclerview` | 1.3.2 | Listas recicladas + `ListAdapter`/`DiffUtil` |
| `androidx.swiperefreshlayout` | 1.1.0 | Pull to refresh |
| `androidx.coordinatorlayout` | 1.2.0 | `layout_behavior`, FAB anclados |
| `com.airbnb.android:lottie` | 6.4.0 | Avatar animado del asistente |
 
**Subidas:** `material` 1.10.0 → **1.12.0** (obligatorio: es la primera
versión que expone `colorSurfaceContainer*`, sin la cual `themes.xml`
falla con *resource attr not found*), `appcompat` 1.6.1 → 1.7.0,
`activity` 1.8.0 → 1.9.3.
 
**Añadido en `compileOptions`:** `encoding = "UTF-8"`. Sin fijarlo,
`javac` usa la codificación del sistema (windows-1252 en Windows) y los
comentarios en castellano de los fuentes UTF-8 se compilan corruptos.
Hace el build reproducible entre máquinas.
 
**Pendiente de decisión:** las dependencias de Paho MQTT se mantienen
pero **siguen sin usarse** desde la app. Su único consumidor potencial
era el switch de modo INSERTAR/ELIMINAR, ya eliminado. Si no se decide
publicar en `frigorifico/modo` desde el móvil, conviene retirarlas.
 
---
 
## 9. Pendientes conocidos
 
1. **Animación Lottie**: colocar `assistant_avatar.json` en
   `app/src/main/assets/` (ver `README_avatar.txt`). Sin él la app
   funciona con el avatar vectorial de respaldo.
2. **`network_security_config`**: hoy `usesCleartextTraffic="true"` abre
   HTTP plano para toda la app. Lo correcto es permitirlo **solo** para
   la IP del backend y forzar TLS en el resto.
3. **`nutriScore` en `RegistroResponse`** (backend) — ver sección 5.
4. **Renombrar `rfidTag`**: el campo ya no es un UID de lector RFID sino
   la clase que devolverá el modelo de visión. Se mantiene el nombre en
   ambos lados por ahora; renombrarlo requiere tocar app, backend y
   columna a la vez.
5. **Árbol legacy duplicado** `SmartFridge_ANDROID/SmartFridge/SmartFridge/`:
   copia completa del proyecto antiguo (Servlets, JDBC, `Android.Logic.*`)
   dentro del módulo Android. No se ha tocado en esta fase, pero debería
   eliminarse o archivarse fuera del módulo.
6. **`java.time`**: se usa `SimpleDateFormat` porque `minSdk = 24`.
   Habilitar `coreLibraryDesugaringEnabled` permitiría migrar.
---
 
## 10. Verificación realizada
 
No fue posible compilar (el SDK de Android vive en el host Windows), así
que la verificación se hizo por análisis estático:
 
- **XML bien formado**: los 40 ficheros de `res/` + manifest parsean sin
  error.
- **Referencias cruzadas**: todos los `R.layout`, `R.id`, `R.drawable`,
  `R.string`, `R.color`, `R.dimen`, `R.style`, `R.menu` usados desde
  Java y desde XML resuelven contra un recurso definido. Únicas
  excepciones: estilos y cadenas que aporta la librería Material
  (`Widget.Material3.Button.TonalButton`,
  `appbar_scrolling_view_behavior`).
- **IDs por pantalla**: cada clase Java solo referencia `R.id` presentes
  en el layout o menú que esa clase infla.
- **Clases declaradas**: todas las del manifest y del `nav_graph`
  existen en disco.
- **Sintaxis Java**: `javac` sobre los 26 fuentes; los 957 errores son
  **todos** de dependencias ausentes (`cannot find symbol`,
  `package … does not exist`) o cascadas suyas. Cero errores de sintaxis.
- **Recursos huérfanos**: cero cadenas, colores, dimensiones, estilos o
  drawables sin uso.
**Queda por hacer en Android Studio**: *Sync Project with Gradle Files*
(hay dependencias nuevas) y una ejecución real para validar el
comportamiento visual y las transiciones.