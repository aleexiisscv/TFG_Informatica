# Fase 10 — Firmware ESP32-CAM (cliente HTTP multipart)
 
Segunda placa física del sistema (además de `ubicua.ino`, que sigue
gestionando DHT11/puerta/agua por MQTT). Esta ESP32-CAM (módulo
AI-Thinker) NO usa MQTT en esta fase: solo captura un JPEG y lo sube por
HTTP multipart manual (sin `HTTPClient`, sobre `WiFiClient` en crudo) a
`POST /api/vision/analizar` del backend de la Fase 9, campo `imagen`.
 
Archivo entregado: `fase10_esp32cam.ino` (único archivo, autocontenido).
 
## Puntos clave del firmware
 
- **Desactivación del detector de brownout** (`WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0)`)
  antes de `esp_camera_init()`: sin esto, la mayoría de placas AI-Thinker
  se reinician en bucle por el pico de corriente del sensor OV2640 al
  arrancar — defecto de diseño muy conocido de estos módulos "clon".
- Pines de cámara fijos por hardware (mapeo estándar AI-Thinker).
- `psramFound()` decide `frame_size`/`fb_count`: SVGA + doble buffer con
  PSRAM, VGA + buffer único sin ella.
- `pixel_format = PIXFORMAT_JPEG` para no tener que convertir el frame
  en la propia placa.
- Multipart construido a mano (boundary, `Content-Disposition: form-data;
  name="imagen"`, `Content-Type: image/jpeg`), con `Content-Length`
  calculado como cabecera+JPEG+pie exactos — si no coincide, Tomcat
  trunca la lectura del body.
- El JPEG se envía a `cliente.write()` en trozos de 1 KB, no como un
  único `String` (evitaría duplicar en RAM un JPEG de varias decenas/
  cientos de KB en una placa con memoria muy limitada).
- `esp_camera_fb_return(fb)` se llama justo después de terminar de
  escribir los bytes JPEG al socket TCP, ANTES de esperar la respuesta
  HTTP — requisito explícito del entregable, y además libera antes el
  buffer de cámara.
- Dos timeouts independientes al leer la respuesta (uno hasta el primer
  byte, otro para la lectura completa) para que un socket a medio cerrar
  no cuelgue la placa para siempre.
## Prueba de integración (lo pedido para esta fase)
 
Sin sensor de puerta ni MQTT todavía: `capturarYEnviarFoto()` se llama
una única vez al final de `setup()`, tras una cuenta atrás de 10 s para
colocar el producto delante del objetivo.
 
Añadido de propina (no pedido explícitamente, pero se justificó en el
chat): un disparador manual por Monitor Serie en `loop()` (enviar `c` +
Enter) para repetir la captura sin recompilar/reiniciar la placa —
subir un sketch a una ESP32-CAM es más lento que a un ESP32 normal (sin
USB nativo, adaptador FTDI + puente en IO0).
 
## Configuración a editar antes de compilar
 
`WIFI_SSID` / `WIFI_PASSWORD`, `SERVIDOR_IP` (IP LOCAL del ordenador que
corre `smartfridge-backend`, NO `localhost` — la ESP32-CAM y el backend
deben estar en la misma red WiFi), `SERVIDOR_PUERTO` (8081).
 
## Pendiente / próximos pasos naturales
 
- Cableado real de la ESP32-CAM (fuente 5V estable — el pico de corriente
  del flash/sensor es también la causa de muchos "brownout" que ni
  siquiera desactivar el detector arregla si la alimentación es floja).
- Integrar el disparo real por sensor de puerta (posiblemente vía MQTT
  desde `ubicua.ino` hacia esta placa, o replicando el sensor magnético
  también en la ESP32-CAM) en vez del disparador manual de pruebas.
- Revisar si el catálogo de productos necesita una estrategia de
  identificador más robusta que el string libre que devuelve Gemini (ver
  nota ya dejada en `Fase9_Vision_Gemini.md` sobre `responseSchema` con
  enum de `rfid_tag` conocidos).