package servlets;

import db.ConnectionDB;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;

import com.google.gson.Gson;


import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import logic.Log;
import mqtt.MQTTBroker;
import mqtt.MQTTPublisher;
import mqtt.MQTTSuscriber;

@WebServlet("/databaseAction")
public class DatabaseServlet extends HttpServlet {
    
    private static final long serialVersionUID = 1L;
    public static String modo = "";
    public static String temperatura = "";
    public static String humedad = "";
    public static String puerta = "";
    public static String rfid = "";
    private static List<Map<String, Object>> productos ;
    private static List<Map<String, Object>> inventarios ;
    private static List<Map<String, Object>> usuarios ;
    private static List<Map<String, Object>> sensores ;
    private static List<Map<String, Object>> registros ;
    //conexion al mqtt
    private static MQTTBroker broker = MQTTBroker.getInstance();
    private static MQTTSuscriber suscriber = new MQTTSuscriber(); 
        
        
    

    public DatabaseServlet() {
        super();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ConnectionDB connectionDB = new ConnectionDB();
        Connection con = null;
        PrintWriter out = response.getWriter();
    
        // Suscribirse a un tópico MQTT
        suscriber.suscribeTopic(broker, "frigorifico/#");
    
        try {
            // Obtener conexión
            con = connectionDB.obtainConnection(true);
            if (con != null) {
                // Obtener datos de todas las tablas
                List<Map<String, Object>> productos = ConnectionDB.GetProductos(con);
                List<Map<String, Object>> inventarios = ConnectionDB.GetInventario(con);
                List<Map<String, Object>> usuarios = ConnectionDB.GetUsuarios(con);
                List<Map<String, Object>> sensores = ConnectionDB.GetSensores(con);
                List<Map<String, Object>> registros = ConnectionDB.GetRegistros(con);
    
                // Crear un mapa para contener todos los datos
                Map<String, Object> allData = new HashMap<>();
                allData.put("productos", productos);
                allData.put("inventarios", inventarios);
                allData.put("usuarios", usuarios);
                allData.put("sensores", sensores);
                allData.put("registros", registros);
                allData.put("modo", this.modo);
    
                // Convertir el mapa a JSON
                Gson gson = new Gson();
                String jsonAllData = gson.toJson(allData);
    
                
                
                // Enviar JSON al frontend
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                out.print(jsonAllData);
                out.flush();
    
                connectionDB.closeTransaction(con); // Confirmar la transacción si es necesario
            } else {
                out.println("Failed to obtain a connection to the database.");
            }
        } finally {
            if (con != null) {
                connectionDB.closeConnection(con); // Cerrar la conexión
                Log.log.info("iteracion completada SERVIDOR WEB");
            }
            out.close();
        }
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ConnectionDB connectionDB = new ConnectionDB();
        Connection con = null;
        PrintWriter out = response.getWriter();

        // Obtener conexión
        con = connectionDB.obtainConnection(true);
        if (con != null) {
            // Obtener datos de todas las tablas
            this.productos = ConnectionDB.GetProductos(con);
            this.inventarios = ConnectionDB.GetInventario(con);
            this.usuarios = ConnectionDB.GetUsuarios(con);
            this.sensores = ConnectionDB.GetSensores(con);
            this.registros = ConnectionDB.GetRegistros(con);
            
            // Crear un mapa para contener todos los datos
            Map<String, Object> allData = new HashMap<>();
            allData.put("productos", productos);
            allData.put("inventarios", inventarios);
            allData.put("usuarios", usuarios);
            allData.put("sensores", sensores);
            allData.put("registros", registros);
            allData.put("modo", this.modo);

            
            //MQTTPublisher.publish(broker, "testRecieve", this.modo);

            // Convertir el mapa a JSON
            Gson gson = new Gson();
            String jsonAllData = gson.toJson(allData);

            Log.log.info("JSON: " + jsonAllData);
            
            // Enviar JSON al frontend
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            out.print(jsonAllData);
            out.flush();
            
            connectionDB.closeTransaction(con); // Confirmar la transacción si es necesario
        } else {
            out.println("Failed to obtain a connection to the database.");
        }
        if (con != null) {
            connectionDB.closeConnection(con); // Cerrar la conexión
            Log.log.info("iteracion completada en ANDROID");
        }
        out.close();
    }
    
    
    
    
}

