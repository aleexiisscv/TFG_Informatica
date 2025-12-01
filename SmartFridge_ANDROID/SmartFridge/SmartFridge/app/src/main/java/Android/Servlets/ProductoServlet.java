package Android.Servlets;

import Android.db.ConnectionDB;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import Android.Logic.Producto;


@WebServlet("/addProducto")
public class ProductoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public ProductoServlet() {
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
        String rfidTag = request.getParameter("rfid_tag");
        String nombre = request.getParameter("nombre");
        char nutriScore = request.getParameter("nutri_score").charAt(0);
        int plazoCaducidad = Integer.parseInt(request.getParameter("plazo_caducidad"));

        Producto producto = new Producto(rfidTag, nombre, nutriScore, plazoCaducidad);
        boolean exito = ConnectionDB.insertarProducto(producto, con);
        if (exito) {
            out.println("Producto añadido con éxito.");
        } else {
            out.println("Error al añadir el producto.");
        }}else {
            out.println("Failed to obtain a connection to the database.");
        }
        if (con != null) {
            connectionDB.closeConnection(con); // Cerrar la conexión
        }
        out.close();
    }
}