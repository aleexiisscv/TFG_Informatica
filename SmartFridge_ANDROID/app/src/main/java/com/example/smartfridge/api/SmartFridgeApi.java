package com.example.smartfridge.api;

import java.util.List;

import com.example.smartfridge.api.dto.AuthResponse;
import com.example.smartfridge.api.dto.ChatRequestDto;
import com.example.smartfridge.api.dto.ChatResponseDto;
import com.example.smartfridge.api.dto.InventarioDto;
import com.example.smartfridge.api.dto.LoginRequest;
import com.example.smartfridge.api.dto.ProductoDto;
import com.example.smartfridge.api.dto.RegisterRequest;
import com.example.smartfridge.api.dto.RegistroDto;
import com.example.smartfridge.api.dto.SensorDto;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

/**
 * Contrato de la API REST del backend Spring Boot. Cada método
 * corresponde 1:1 a un endpoint real de un @RestController del
 * backend (com.smartfridge.controller.*) — EXCEPTO login()/registrar(),
 * marcados como PENDIENTES: el AuthController todavía no existe.
 *
 * Es deliberadamente una interfaz sin implementación: Retrofit genera
 * la implementación real (una clase proxy) en tiempo de ejecución a
 * partir de esta declaración + las anotaciones. Este archivo es, en sí
 * mismo, la documentación viva del contrato entre app y backend.
 */
public interface SmartFridgeApi {

    // --- Productos ---
    @GET("api/productos")
    Call<List<ProductoDto>> listarProductos();

    @POST("api/productos")
    Call<ProductoDto> crearProducto(@Body ProductoDto producto);

    // --- Inventario (solo lectura: las altas/bajas van por MQTT vía RFID) ---
    @GET("api/inventario")
    Call<List<InventarioDto>> listarInventario();

    // --- Sensores (solo lectura: el estado lo escribe el listener MQTT) ---
    @GET("api/sensores")
    Call<List<SensorDto>> listarSensores();

    // --- Registros (solo lectura) ---
    @GET("api/registros")
    Call<List<RegistroDto>> listarRegistros(@Query("tipo") String tipoRegistro);

    // --- Asistente conversacional (Fase 11) ---
    /**
     * Consulta al agente RAG. A diferencia del resto de endpoints, esta
     * llamada puede tardar VARIOS SEGUNDOS: el backend recopila el
     * contexto del frigorífico y espera a que Gemini genere la
     * respuesta. Por eso RetrofitClient define un readTimeout mucho más
     * largo que los 10 s que bastaban para los endpoints de datos.
     */
    @POST("api/asistente/chat")
    Call<ChatResponseDto> chat(@Body ChatRequestDto peticion);

    // --- Visión artificial (Fase 9/10): identificación de producto por fotografía ---
    /**
     * Sube la fotografía tomada en {@code ScanProductActivity} al mismo
     * endpoint que ya consume el nodo de visión de la propia ESP32-CAM
     * (ver {@code VisionController} en el backend). Si Gemini reconoce
     * el producto, el servidor da de alta la unidad en el inventario en
     * el mismo paso y responde 201 Created; si no lo reconoce responde
     * 422, y si el identificador no existe en el catálogo responde 404.
     * Ambos casos de error se resuelven en la Activity dejando caer el
     * flujo al alta manual, sin necesidad de un segundo endpoint.
     */
    @Multipart
    @POST("api/vision/analizar")
    Call<InventarioDto> analizarImagen(@Part MultipartBody.Part imagen);

    // --- Autenticación — PENDIENTE: endpoint aún no implementado en el backend ---
    @POST("api/auth/login")
    Call<AuthResponse> login(@Body LoginRequest request);

    @POST("api/auth/registro")
    Call<AuthResponse> registrar(@Body RegisterRequest request);
}
