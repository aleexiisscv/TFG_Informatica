// fase10_esp32cam.ino
//
// Firmware de la capa de Percepción — nodo de visión (TFG SmartFridge).
//
// Placa: ESP32-CAM (módulo AI-Thinker, sensor OV2640).
//
// Rol de este .ino frente al firmware ya existente (Arduino_entregable/ubicua/ubicua.ino):
// son DOS placas físicas distintas con responsabilidades separadas.
// "ubicua.ino" sigue siendo el nodo de sensores (DHT11, puerta, agua) y
// habla por MQTT con el backend. Este nodo NO usa MQTT: es un cliente
// HTTP dedicado a capturar un fotograma JPEG y subirlo por POST
// multipart a Spring Boot (POST /api/vision/analizar, campo "imagen"),
// que es quien orquesta la llamada a Gemini y la actualización del
// inventario. Mantener esta separación evita mezclar en el mismo sketch
// dos protocolos de transporte distintos (MQTT vs HTTP) y respeta el
// mismo principio ya aplicado en el backend: el ESP32 no razona sobre
// negocio, solo transporta datos crudos (aquí, píxeles en vez de
// lecturas de sensor).
//
// -----------------------------------------------------------------------
// SINCRONIZACIÓN CON LA FASE 13 DEL BACKEND (10/09/2026)
// -----------------------------------------------------------------------
// El backend cambió el contrato y este firmware se había quedado atrás.
// Dos cambios, en direcciones opuestas:
//
//   1) SUBIDA (la placa habla al backend). /api/vision/** dejó de ser
//      público: SecurityConfig lo marca como .authenticated(). Un POST
//      sin credencial recibe ahora 401 y la foto no llega a Gemini.
//      Como una cámara no puede sostener un JWT de usuario (no tiene
//      teclado ni puede renovar un token caducado), el backend admite
//      para esta ruta —y solo para esta— un secreto de dispositivo en
//      la cabecera "X-Device-Key". Es lo que se añade abajo.
//
//   2) DESCARGA (el backend habla a la placa). La Fase 13 añadió una
//      orquestación opcional: al cerrarse la puerta, el servidor
//      DESCARGA un JPEG haciendo GET a esta placa. El backend espera
//      encontrar aquí un servidor HTTP mínimo que responda a /capture
//      con image/jpeg, y hasta ahora este sketch solo sabía ser cliente.
//      Se añade ese servidor.
//
// La orquestación sigue APAGADA en el backend
// (smartfridge.camara.disparo-automatico = false), porque el sensor
// magnético de puerta produce falsos contactos. Por tanto este servidor
// queda a la escucha sin que nadie lo llame: es pasivo, no dispara
// capturas por su cuenta y no altera el comportamiento actual (foto
// única al arrancar + disparador manual por Monitor Serie). El día que
// se ponga el flag a true, la placa ya está preparada.
//
// ADVERTENCIA DE SEGURIDAD (igual que en application.yml del backend):
// las credenciales de abajo son de ejemplo. Sustitúyelas por las
// vuestras antes de compilar, y no subas el .ino con credenciales
// reales a un repositorio público.

#include <WiFi.h>
#include <WebServer.h>
#include "esp_camera.h"
#include "soc/soc.h"           // Desactivación del detector de brownout
#include "soc/rtc_cntl_reg.h"  // (ver justificación en setup())

// =======================================================================
// Configuración — EDITAR antes de compilar
// =======================================================================

// --- Red WiFi ---
const char* WIFI_SSID = "VodafoneSw";
const char* WIFI_PASSWORD = "Vodafone.Sw";

// --- Backend Spring Boot ---
// IP LOCAL del ordenador donde corre smartfridge-backend (ver "ipconfig"/
// "ip a" en esa máquina; la ESP32-CAM y el backend deben estar en la
// MISMA red WiFi). NO uses "localhost"/"127.0.0.1": eso apuntaría a la
// propia ESP32-CAM, no a tu ordenador.
const char* SERVIDOR_IP = "192.168.0.191";
const uint16_t SERVIDOR_PUERTO = 8081;
const char* RUTA_ENDPOINT = "/api/vision/analizar";

// --- Credencial de dispositivo (Fase 13) ---
// DEBE coincidir EXACTAMENTE con smartfridge.seguridad.clave-dispositivo
// del backend (variable de entorno DEVICE_KEY). Si aquí queda vacío no
// se envía la cabecera, y como /api/vision/** exige autenticación desde
// la Fase 13, el backend responderá 401 y descartará la foto.
//
// El backend compara este valor con MessageDigest.isEqual (tiempo
// constante), así que no hay nada que ganar probando claves parecidas;
// simplemente tiene que ser el mismo string en los dos sitios.
//
// Genera uno así y ponlo en ambos lados:  openssl rand -base64 32
const char* CLAVE_DISPOSITIVO = "x7Kq2mZp9RtLb4VnH8sWdJ3yCfA6geUiN0oQrTxM";
const char* CABECERA_CLAVE_DISPOSITIVO = "X-Device-Key";

// --- Servidor HTTP local (para la orquestación cámara-puerta) ---
// Ruta que el backend consulta: debe coincidir con
// smartfridge.camara.ruta-captura (por defecto "/capture").
const bool HABILITAR_SERVIDOR_CAPTURA = true;
const uint16_t PUERTO_SERVIDOR_LOCAL = 80;
const char* RUTA_CAPTURA = "/capture";

// Margen de espera para la respuesta HTTP. Generoso a propósito: el
// backend, a su vez, espera a Gemini (razonamiento + red), y si el
// modelo principal falla por cuota reintenta con el de respaldo, lo que
// suma una segunda inferencia completa a la misma petición.
const unsigned long TIMEOUT_RESPUESTA_MS = 30000;

// Retardo antes de la primera captura automática, para dar tiempo a
// colocar el producto delante del objetivo tras encender la placa.
const unsigned long RETARDO_PRIMERA_CAPTURA_MS = 10000;

// Margen para recuperar la WiFi si se cae estando la placa encendida.
// Antes esto no hacía falta: el sketch hacía una foto al arrancar y poco
// más. Ahora la placa queda indefinidamente a la escucha, y una sesión
// larga sin reconexión terminaría en una placa viva pero inalcanzable.
const unsigned long TIMEOUT_RECONEXION_WIFI_MS = 15000;

// =======================================================================
// Pines de la cámara — módulo AI-Thinker (fijos por hardware, no editar)
// =======================================================================
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27
#define Y9_GPIO_NUM        35
#define Y8_GPIO_NUM        34
#define Y7_GPIO_NUM        39
#define Y6_GPIO_NUM        36
#define Y5_GPIO_NUM        21
#define Y4_GPIO_NUM        19
#define Y3_GPIO_NUM        18
#define Y2_GPIO_NUM         5
#define VSYNC_GPIO_NUM     25
#define HREF_GPIO_NUM      23
#define PCLK_GPIO_NUM      22

// LED flash integrado (GPIO 4). No se usa en esta fase (no se ha pedido
// iluminación asistida); se deja definido como punto de extensión obvio
// para una fase posterior si las fotos salen oscuras dentro de la nevera.
#define FLASH_GPIO_NUM 4

// Servidor HTTP local. Se declara siempre (ocupa poca RAM) pero solo se
// arranca si HABILITAR_SERVIDOR_CAPTURA es true.
WebServer servidorCaptura(PUERTO_SERVIDOR_LOCAL);

// Declaraciones adelantadas: el .ino de Arduino las genera solo, pero
// escribirlas explícitamente evita sorpresas cuando una función devuelve
// un tipo que el preprocesador no sabe adelantar.
void iniciarCamara();
void conectarWiFi();
bool asegurarWiFi();
void iniciarServidorCaptura();
void manejarCaptura();
void manejarEstado();
void manejarNoEncontrado();
bool capturarYEnviarFoto();
int leerCodigoEstado(WiFiClient& cliente);
void explicarCodigoEstado(int codigo);

// =======================================================================
// setup()
// =======================================================================
void setup() {
    Serial.begin(115200);
    Serial.println();
    Serial.println("=== SmartFridge — Nodo de visión (ESP32-CAM) ===");

    // El pico de corriente que da el sensor OV2640 al arrancar el flujo
    // de vídeo hace saltar el detector de brownout de la ESP32 en la
    // inmensa mayoría de placas AI-Thinker (defecto de diseño conocido
    // de estos módulos "clon", con regulador de 3.3V infradimensionado).
    // Sin esto, la placa se reinicia en bucle justo al llamar a
    // esp_camera_init(). Se desactiva ANTES de inicializar la cámara.
    WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);

    iniciarCamara();
    conectarWiFi();
    iniciarServidorCaptura();

    if (strlen(CLAVE_DISPOSITIVO) == 0) {
        Serial.println();
        Serial.println("*******************************************************");
        Serial.println("AVISO: CLAVE_DISPOSITIVO está vacía.");
        Serial.println("Desde la Fase 13 el backend exige autenticación en");
        Serial.println("/api/vision/**, así que la subida recibirá un 401 y la");
        Serial.println("foto se descartará. Define el MISMO valor en los dos");
        Serial.println("lados: aquí arriba, y en el backend como DEVICE_KEY");
        Serial.println("(smartfridge.seguridad.clave-dispositivo).");
        Serial.println("*******************************************************");
    }

    Serial.printf("Primera captura automática en %lu s. Coloca el producto frente al objetivo...\n",
                  RETARDO_PRIMERA_CAPTURA_MS / 1000);
    for (unsigned long restante = RETARDO_PRIMERA_CAPTURA_MS / 1000; restante > 0; restante--) {
        Serial.printf("  %lu...\n", restante);
        // Se atiende el servidor durante la cuenta atrás: si el backend
        // llamase justo ahora, no debe encontrarse la placa "sorda".
        unsigned long finSegundo = millis() + 1000;
        while (millis() < finSegundo) {
            if (HABILITAR_SERVIDOR_CAPTURA) {
                servidorCaptura.handleClient();
            }
            delay(10);
        }
    }

    capturarYEnviarFoto();

    Serial.println();
    Serial.println("Prueba de integración completada.");
    Serial.println("Envía 'c' + Enter por el Monitor Serie para repetir la captura sin reiniciar la placa.");
}

// =======================================================================
// loop()
// =======================================================================
// Dos responsabilidades, ninguna de las cuales dispara capturas por su
// cuenta:
//
//   - Atender el servidor HTTP local. Es el backend quien decide cuándo
//     pedir una foto (y hoy, con disparo-automatico=false, no la pide
//     nunca). La placa se limita a estar disponible.
//   - El disparador manual por Monitor Serie, que ya existía: repetir la
//     prueba solo con setup() obligaría a pulsar RESET cada vez, molesto
//     en una ESP32-CAM que no siempre tiene botón accesible.
void loop() {
    if (HABILITAR_SERVIDOR_CAPTURA) {
        servidorCaptura.handleClient();
    }

    if (Serial.available() > 0) {
        char comando = Serial.read();
        if (comando == 'c' || comando == 'C') {
            capturarYEnviarFoto();
        }
    }
}

// =======================================================================
// Inicialización de la cámara
// =======================================================================
void iniciarCamara() {
    camera_config_t config;
    config.ledc_channel = LEDC_CHANNEL_0;
    config.ledc_timer = LEDC_TIMER_0;
    config.pin_d0 = Y2_GPIO_NUM;
    config.pin_d1 = Y3_GPIO_NUM;
    config.pin_d2 = Y4_GPIO_NUM;
    config.pin_d3 = Y5_GPIO_NUM;
    config.pin_d4 = Y6_GPIO_NUM;
    config.pin_d5 = Y7_GPIO_NUM;
    config.pin_d6 = Y8_GPIO_NUM;
    config.pin_d7 = Y9_GPIO_NUM;
    config.pin_xclk = XCLK_GPIO_NUM;
    config.pin_pclk = PCLK_GPIO_NUM;
    config.pin_vsync = VSYNC_GPIO_NUM;
    config.pin_href = HREF_GPIO_NUM;
    config.pin_sscb_sda = SIOD_GPIO_NUM;
    config.pin_sscb_scl = SIOC_GPIO_NUM;
    config.pin_pwdn = PWDN_GPIO_NUM;
    config.pin_reset = RESET_GPIO_NUM;
    config.xclk_freq_hz = 20000000;
    config.pixel_format = PIXFORMAT_JPEG;  // Gemini recibe JPEG directamente; sin esto habría que
                                            // convertir el frame en la propia ESP32 antes de enviarlo.

    // Gemini no necesita más resolución que la que un ojo humano usaría
    // para reconocer un producto de supermercado; subir a UXGA solo
    // infla el base64 (y el tiempo de subida por WiFi) sin mejorar el
    // reconocimiento. Si hay PSRAM (caso normal en AI-Thinker con OV2640)
    // se usa doble buffer para poder capturar mientras se envía el
    // anterior; sin PSRAM, un único buffer más pequeño para no agotar la
    // RAM interna (más escasa) de la ESP32.
    if (psramFound()) {
        config.frame_size = FRAMESIZE_SVGA;  // 800x600
        config.jpeg_quality = 10;            // 0-63; menor = más calidad/peso
        config.fb_count = 2;
        config.grab_mode = CAMERA_GRAB_LATEST;
    } else {
        config.frame_size = FRAMESIZE_VGA;   // 640x480
        config.jpeg_quality = 12;
        config.fb_count = 1;
        config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
    }

    esp_err_t resultado = esp_camera_init(&config);
    if (resultado != ESP_OK) {
        Serial.printf("ERROR FATAL: esp_camera_init() falló (0x%x). Revisa el cableado y el modelo de placa.\n",
                      resultado);
        // Sin cámara no hay nada más que hacer en este nodo: se detiene
        // aquí en vez de seguir a un WiFi/loop que nunca podría cumplir
        // su función.
        while (true) {
            delay(1000);
        }
    }

    Serial.println("Cámara inicializada correctamente.");
}

// =======================================================================
// Conexión WiFi (modo estación)
// =======================================================================
void conectarWiFi() {
    WiFi.mode(WIFI_STA);
    // Sin esto, el modem-sleep de la ESP32 añade latencia (y a veces
    // pérdidas) a las peticiones entrantes. Daba igual cuando la placa
    // solo hacía de cliente; ahora que atiende un servidor, importa.
    WiFi.setSleep(false);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    Serial.print("Conectando a WiFi");
    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print('.');
    }

    Serial.println();
    Serial.print("WiFi conectada. IP de la ESP32-CAM: ");
    Serial.println(WiFi.localIP());
    Serial.println("Esta es la IP que debe llevar smartfridge.camara.base-url en el backend,");
    Serial.printf("con el formato http://%s:%u\n", WiFi.localIP().toString().c_str(), PUERTO_SERVIDOR_LOCAL);
}

// Reconexión no bloqueante-indefinida: devuelve false en vez de colgar
// la placa para siempre, de modo que quien llama decide qué hacer.
bool asegurarWiFi() {
    if (WiFi.status() == WL_CONNECTED) {
        return true;
    }

    Serial.println("WiFi caída. Reintentando conexión...");
    WiFi.reconnect();

    unsigned long inicio = millis();
    while (WiFi.status() != WL_CONNECTED && (millis() - inicio) < TIMEOUT_RECONEXION_WIFI_MS) {
        delay(250);
    }

    if (WiFi.status() != WL_CONNECTED) {
        Serial.println("ERROR: no se pudo restablecer la WiFi.");
        return false;
    }

    Serial.print("WiFi restablecida. IP: ");
    Serial.println(WiFi.localIP());
    return true;
}

// =======================================================================
// Servidor HTTP local — lado "descarga" del contrato de la Fase 13
// =======================================================================
// El backend (CapturaAutomaticaService) hace GET http://<ip>/capture y
// espera un image/jpeg. No envía credencial: la placa vive en la red
// local del frigorífico y no expone nada más que un fotograma. Si algún
// día este puerto se abriese al exterior, aquí es donde habría que
// exigir la misma X-Device-Key que se usa en la subida.
void iniciarServidorCaptura() {
    if (!HABILITAR_SERVIDOR_CAPTURA) {
        Serial.println("Servidor de captura desactivado por configuración.");
        return;
    }

    servidorCaptura.on(RUTA_CAPTURA, HTTP_GET, manejarCaptura);
    servidorCaptura.on("/", HTTP_GET, manejarEstado);
    servidorCaptura.onNotFound(manejarNoEncontrado);
    servidorCaptura.begin();

    Serial.printf("Servidor de captura escuchando en http://%s:%u%s\n",
                  WiFi.localIP().toString().c_str(), PUERTO_SERVIDOR_LOCAL, RUTA_CAPTURA);
}

void manejarCaptura() {
    camera_fb_t* fb = esp_camera_fb_get();
    if (!fb) {
        Serial.println("GET /capture -> ERROR: no se obtuvo fotograma.");
        servidorCaptura.send(503, "text/plain", "camara no disponible");
        return;
    }
    if (fb->format != PIXFORMAT_JPEG) {
        Serial.println("GET /capture -> ERROR: el fotograma no es JPEG.");
        esp_camera_fb_return(fb);
        servidorCaptura.send(500, "text/plain", "formato inesperado");
        return;
    }

    Serial.printf("GET /capture -> enviando %u bytes de JPEG.\n", fb->len);

    // setContentLength ANTES de send(): así la cabecera lleva la longitud
    // exacta y la respuesta no se envía en chunked, que es lo que espera
    // un cliente HTTP sencillo como el RestClient del backend.
    WiFiClient cliente = servidorCaptura.client();
    servidorCaptura.setContentLength(fb->len);
    servidorCaptura.send(200, "image/jpeg", "");

    // Se escribe a trozos por el mismo motivo que en la subida: no
    // duplicar en RAM un buffer que ya ocupa decenas o cientos de KB.
    const size_t TAMANO_TROZO = 1024;
    size_t enviados = 0;
    while (enviados < fb->len && cliente.connected()) {
        size_t tamanoEsteTrozo = min(TAMANO_TROZO, (size_t)(fb->len - enviados));
        cliente.write(fb->buf + enviados, tamanoEsteTrozo);
        enviados += tamanoEsteTrozo;
    }

    esp_camera_fb_return(fb);
}

// Pequeña página de estado: sirve para comprobar desde el navegador que
// la placa es alcanzable sin gastar una captura.
void manejarEstado() {
    String cuerpo = "SmartFridge - nodo de vision (ESP32-CAM)\n";
    cuerpo += "IP: " + WiFi.localIP().toString() + "\n";
    cuerpo += "RSSI: " + String(WiFi.RSSI()) + " dBm\n";
    cuerpo += "Uptime: " + String(millis() / 1000) + " s\n";
    cuerpo += "Captura: GET " + String(RUTA_CAPTURA) + "\n";
    cuerpo += "Clave de dispositivo configurada: ";
    cuerpo += (strlen(CLAVE_DISPOSITIVO) > 0 ? "si" : "NO");
    cuerpo += "\n";
    servidorCaptura.send(200, "text/plain", cuerpo);
}

void manejarNoEncontrado() {
    servidorCaptura.send(404, "text/plain", "ruta no encontrada");
}

// =======================================================================
// Captura un fotograma y lo envía al backend como multipart/form-data
// =======================================================================
// Construye la petición HTTP a mano (sin HTTPClient ni ninguna librería
// de multipart) sobre un WiFiClient en crudo: exactamente lo pedido en
// el enunciado de esta fase, y también la opción más ligera en RAM para
// una placa con memoria muy limitada — no hay que montar el cuerpo
// completo de la petición en un único String antes de enviarlo (eso
// duplicaría en RAM un JPEG que ya ocupa varias decenas/cientos de KB).
bool capturarYEnviarFoto() {
    if (!asegurarWiFi()) {
        return false;
    }

    camera_fb_t* fb = esp_camera_fb_get();
    if (!fb) {
        Serial.println("ERROR: esp_camera_fb_get() no devolvió ningún fotograma.");
        return false;
    }
    if (fb->format != PIXFORMAT_JPEG) {
        // No debería pasar con pixel_format = PIXFORMAT_JPEG en la
        // configuración, pero se comprueba para no enviar nunca al
        // backend algo que no es la imagen que dice ser.
        Serial.println("ERROR: el fotograma capturado no está en formato JPEG.");
        esp_camera_fb_return(fb);
        return false;
    }

    Serial.printf("Fotograma capturado: %u bytes.\n", fb->len);

    WiFiClient cliente;
    Serial.printf("Conectando con el backend %s:%u...\n", SERVIDOR_IP, SERVIDOR_PUERTO);
    if (!cliente.connect(SERVIDOR_IP, SERVIDOR_PUERTO)) {
        Serial.println("ERROR: no se pudo conectar con el backend. ¿IP correcta? ¿Misma red WiFi? ¿Backend arrancado?");
        esp_camera_fb_return(fb);
        return false;
    }

    // --- Cuerpo multipart/form-data ---
    // El boundary debe ser una cadena que no vaya a aparecer por
    // casualidad dentro del propio JPEG; el prefijo largo y poco común
    // es la práctica estándar para evitarlo.
    const String boundary = "SmartFridgeESP32CAMBoundary7MA4YWxk";

    // "imagen" DEBE coincidir exactamente con
    // @RequestParam("imagen") MultipartFile imagen en VisionController.
    String cabeceraParte;
    cabeceraParte += "--" + boundary + "\r\n";
    cabeceraParte += "Content-Disposition: form-data; name=\"imagen\"; filename=\"captura.jpg\"\r\n";
    cabeceraParte += "Content-Type: image/jpeg\r\n";
    cabeceraParte += "\r\n";

    String pieParte = "\r\n--" + boundary + "--\r\n";

    size_t longitudCuerpo = cabeceraParte.length() + fb->len + pieParte.length();

    // --- Cabeceras HTTP ---
    // Content-Length debe ser el tamaño EXACTO del cuerpo completo
    // (cabecera de parte + bytes JPEG + pie de parte), no solo el de la
    // imagen: si no coincide, Tomcat corta la lectura del body a medio
    // multipart y el backend recibe un archivo corrupto o incompleto.
    cliente.print("POST " + String(RUTA_ENDPOINT) + " HTTP/1.1\r\n");
    cliente.print("Host: " + String(SERVIDOR_IP) + ":" + String(SERVIDOR_PUERTO) + "\r\n");

    // Credencial de dispositivo (Fase 13). Se omite la cabecera entera
    // si no hay clave configurada: enviar "X-Device-Key:" vacía sería
    // indistinguible de un intento fallido y ensucia el log del backend.
    if (strlen(CLAVE_DISPOSITIVO) > 0) {
        cliente.print(String(CABECERA_CLAVE_DISPOSITIVO) + ": " + String(CLAVE_DISPOSITIVO) + "\r\n");
    }

    cliente.print("Content-Type: multipart/form-data; boundary=" + boundary + "\r\n");
    cliente.print("Content-Length: " + String(longitudCuerpo) + "\r\n");
    cliente.print("Connection: close\r\n");  // Simplifica leer la respuesta: el backend cierra el socket al terminar.
    cliente.print("\r\n");

    // --- Cuerpo: cabecera de parte + JPEG a trozos + pie de parte ---
    cliente.print(cabeceraParte);

    const size_t TAMANO_TROZO = 1024;
    size_t enviados = 0;
    while (enviados < fb->len) {
        size_t tamanoEsteTrozo = min(TAMANO_TROZO, (size_t)(fb->len - enviados));
        cliente.write(fb->buf + enviados, tamanoEsteTrozo);
        enviados += tamanoEsteTrozo;
    }

    cliente.print(pieParte);

    // El JPEG ya se copió por completo al socket TCP (que tiene su
    // propio buffer interno): a partir de aquí la placa ya no necesita
    // el fotograma en memoria, así que se libera ANTES de esperar la
    // respuesta del backend, no después. Es el requisito explícito de
    // este entregable y, de paso, dejar el buffer de la cámara libre el
    // mayor tiempo posible es lo que permite que fb_count=2 sirva para
    // algo (poder capturar el siguiente fotograma sin esperar a que
    // termine esta petición HTTP).
    esp_camera_fb_return(fb);
    fb = nullptr;

    Serial.println("Imagen enviada. Esperando respuesta del backend...");

    unsigned long inicioEspera = millis();
    while (cliente.connected() && !cliente.available() && (millis() - inicioEspera) < TIMEOUT_RESPUESTA_MS) {
        delay(10);
    }

    if (!cliente.available()) {
        Serial.println("ERROR: sin respuesta del backend (timeout).");
        cliente.stop();
        return false;
    }

    // Se lee primero la línea de estado por separado. Volcar la respuesta
    // en crudo al Monitor Serie, como se hacía antes, obliga a interpretar
    // a mano un 401 o un 422 — y desde la Fase 13 esos dos códigos son
    // resultados esperables, no anomalías.
    int codigo = leerCodigoEstado(cliente);

    Serial.println("--- Respuesta HTTP del backend ---");
    // Segundo timeout, independiente del de arriba: ese solo cubría la
    // espera hasta el PRIMER byte. Este cubre la lectura completa, por
    // si el backend dejara el socket a medio cerrar (p. ej. un proxy
    // intermedio) — sin él, un caso así colgaría la placa para siempre
    // y solo se recuperaría con un reset físico.
    unsigned long inicioLectura = millis();
    while ((cliente.connected() || cliente.available())
           && (millis() - inicioLectura) < TIMEOUT_RESPUESTA_MS) {
        while (cliente.available()) {
            Serial.write(cliente.read());
            inicioLectura = millis();  // hay tráfico: se reinicia el margen
        }
    }
    Serial.println();
    Serial.println("-----------------------------------");

    explicarCodigoEstado(codigo);

    cliente.stop();
    return codigo >= 200 && codigo < 300;
}

// Lee la línea de estado ("HTTP/1.1 200 OK") y devuelve el código
// numérico, o -1 si no se pudo interpretar.
int leerCodigoEstado(WiFiClient& cliente) {
    String lineaEstado = cliente.readStringUntil('\n');
    lineaEstado.trim();
    Serial.println(lineaEstado);

    int primerEspacio = lineaEstado.indexOf(' ');
    if (primerEspacio < 0 || lineaEstado.length() < primerEspacio + 4) {
        return -1;
    }
    return lineaEstado.substring(primerEspacio + 1, primerEspacio + 4).toInt();
}

// Traduce el código a la causa concreta dentro de este sistema. Los
// mensajes se corresponden con los handlers de GlobalExceptionHandler y
// con las reglas de SecurityConfig.
void explicarCodigoEstado(int codigo) {
    switch (codigo) {
        case 200:
        case 201:
            Serial.println("OK: el backend identificó el producto y actualizó el inventario.");
            break;
        case 401:
            Serial.println("401 NO AUTORIZADO. Desde la Fase 13, /api/vision/** exige credencial.");
            Serial.println("  -> Comprueba que CLAVE_DISPOSITIVO (arriba en este sketch) coincide");
            Serial.println("     EXACTAMENTE con DEVICE_KEY en el backend. Si DEVICE_KEY está");
            Serial.println("     vacía en el backend, la ruta solo acepta JWT de usuario y esta");
            Serial.println("     placa no puede subir fotos.");
            break;
        case 403:
            Serial.println("403 PROHIBIDO: la credencial llegó pero no autoriza esta ruta.");
            break;
        case 404:
            Serial.println("404: el identificador devuelto por Gemini no existe en el catálogo,");
            Serial.println("  o la ruta del endpoint no es la correcta.");
            break;
        case 422:
            Serial.println("422: Gemini respondió DESCONOCIDO. No es un fallo del sistema:");
            Serial.println("  la salida estructurada de la Fase 13 obliga al modelo a elegir un");
            Serial.println("  producto del catálogo o a admitir que no lo reconoce. Prueba con");
            Serial.println("  mejor luz, el producto más centrado, o revisa que esté dado de alta.");
            break;
        case 502:
            Serial.println("502: el backend no pudo hablar con Gemini (clave de API, cuota o red).");
            break;
        case -1:
            Serial.println("No se pudo interpretar la línea de estado HTTP.");
            break;
        default:
            Serial.printf("Código HTTP %d. Revisa el cuerpo de la respuesta de arriba.\n", codigo);
            break;
    }
}
