package Android.Servlets;

import Android.db.ConnectionDB;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;


import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import Android.Logic.Logs;
import Android.Mqtt.MQTTBroker;
import Android.Mqtt.MQTTPublisher;
import Android.Mqtt.MQTTSuscriber;

@WebServlet("/databaseAction")
public class DatabaseServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public DatabaseServlet() {
        super();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ConnectionDB connectionDB = new ConnectionDB();
        Connection con = null;
        PrintWriter out = response.getWriter();
        
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
        if (con != null) {
            connectionDB.closeConnection(con); // Cerrar la conexión
        }
        out.close();
    }

}

