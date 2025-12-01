/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Android.Logic;

/**
 *
 * @author isaba
 */


import Android.Database.ConectionDDBB;
import java.sql.*;
import Android.Logic.Logs;

public class Inventario {
    private int id;
    private String productoId;
    private Timestamp fechaEntrada;
    private Timestamp fechaCaducidad;

    // Constructor
    public Inventario(int id, String productoId, Timestamp fechaEntrada, Timestamp fechaCaducidad) {
        this.id = id;
        this.productoId = productoId;
        this.fechaEntrada = fechaEntrada;
        this.fechaCaducidad = fechaCaducidad;
    }

    // Método para añadir un producto al inventario y registrar la operación
    public static boolean añadirProducto(String rfidTag) {
        ConectionDDBB conector = new ConectionDDBB();
        Connection con = null;
        try {
            con = conector.obtainConnection(true);
            // Consultar plazo de caducidad del producto
            PreparedStatement psProducto = con.prepareStatement("SELECT plazo_caducidad FROM producto WHERE rfid_tag = ?");
            psProducto.setString(1, rfidTag);
            ResultSet rsProducto = psProducto.executeQuery();
            if (!rsProducto.next()) {
                return false;
            }
            int plazoCaducidad = rsProducto.getInt("plazo_caducidad");
            Timestamp fechaCaducidad = new Timestamp(System.currentTimeMillis() + plazoCaducidad * 86400000L);

            // Insertar producto en inventario
            PreparedStatement psInventario = con.prepareStatement("INSERT INTO inventario (producto_id, fecha_entrada, fecha_caducidad) VALUES (?, CURRENT_TIMESTAMP, ?)");
            psInventario.setString(1, rfidTag);
            psInventario.setTimestamp(2, fechaCaducidad);
            psInventario.executeUpdate();

            // Registrar la operación en la tabla de registros
            PreparedStatement psRegistro = con.prepareStatement("INSERT INTO registro (tipo_registro, id_producto, fecha) VALUES ('entrada', ?, CURRENT_TIMESTAMP)");
            psRegistro.setString(1, rfidTag);
            psRegistro.executeUpdate();

            return true;
        } catch (SQLException e) {
            Logs.error("Error: ", e.toString());
            return false;
        } finally {
            if (con != null) {
                conector.closeConnection(con);
            }
        }
    }

    // Método para eliminar el producto más antiguo del inventario y registrar la operación
    public static boolean eliminarProducto(String rfidTag) {
        ConectionDDBB conector = new ConectionDDBB();
        Connection con = null;
        try {
            con = conector.obtainConnection(true);
            // Eliminar el producto más antiguo
            PreparedStatement ps = con.prepareStatement("DELETE FROM inventario WHERE producto_id = ? ORDER BY fecha_entrada ASC LIMIT 1");
            ps.setString(1, rfidTag);
            int affectedRows = ps.executeUpdate();
            if (affectedRows == 0) {
                return false;
            }

            // Registrar la operación en la tabla de registros
            PreparedStatement psRegistro = con.prepareStatement("INSERT INTO registro (tipo_registro, id_producto, fecha) VALUES ('salida', ?, CURRENT_TIMESTAMP)");
            psRegistro.setString(1, rfidTag);
            psRegistro.executeUpdate();

            return true;
        } catch (SQLException e) {
            Logs.error("Error: " , e.toString());
            return false;
        } finally {
            if (con != null) {
                conector.closeConnection(con);
            }
        }
    }
}
