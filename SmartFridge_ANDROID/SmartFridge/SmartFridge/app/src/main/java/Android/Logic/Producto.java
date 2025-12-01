package Android.Logic;

import java.sql.Connection;
import java.sql.PreparedStatement;
import Android.Database.ConectionDDBB;
import java.sql.*;
import java.util.ArrayList;
import Android.Logic.Logs;

public class Producto {
    private String rfidTag;
    private String nombre;
    private char nutriScore;
    private int plazoCaducidad; // días en que caduca el producto
    private static ConectionDDBB conector = new ConectionDDBB();

    // Constructor
    public Producto(String rfidTag, String nombre, char nutriScore, int plazoCaducidad) {
        this.rfidTag = rfidTag;
        this.nombre = nombre;
        this.nutriScore = nutriScore;
        this.plazoCaducidad = plazoCaducidad;
    }

    // Getters y Setters
    public String getRfidTag() {
        return rfidTag;
    }

    public String getNombre() {
        return nombre;
    }

    public char getNutriScore() {
        return nutriScore;
    }

    public int getPlazoCaducidad() {
        return plazoCaducidad;
    }
    // Métodos de Base de Datos
    public static boolean insertarProducto(Producto producto) {
        Connection con = null;
        try {
            con = conector.obtainConnection(true);
            PreparedStatement ps = con.prepareStatement("INSERT INTO productos (rfid_tag, nombre, nutri_score, plazo_caducidad) VALUES (?, ?, ?, ?)");
            ps.setString(1, producto.getRfidTag());
            ps.setString(2, producto.getNombre());
            ps.setString(3, String.valueOf(producto.getNutriScore()));
            ps.setInt(4, producto.getPlazoCaducidad());
            ps.executeUpdate();
            conector.closeTransaction(con);
            return true;
        } catch (SQLException e) {
            Logs.error("Error: " ,e.toString());
            conector.cancelTransaction(con);
        } finally {
            conector.closeConnection(con);
        }
        return false;
    }

    public static ArrayList<Producto> obtenerProductos() {
        ArrayList<Producto> productos = new ArrayList<>();
        Connection con = null;
        try {
            con = conector.obtainConnection(true);
            PreparedStatement ps = con.prepareStatement("SELECT * FROM productos");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                productos.add(new Producto(
                        rs.getString("rfid_tag"),
                        rs.getString("nombre"),
                        rs.getString("nutri_score").charAt(0),
                        rs.getInt("plazo_caducidad")
                ));
            }
        } catch (SQLException e) {
            Logs.error("Error: " , e.toString());
        } finally {
            conector.closeConnection(con);
        }
        return productos;
    }
}
