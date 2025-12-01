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
import java.util.HashMap;
import java.util.Map;
import Android.Logic.Logs;

public class Registro {
    private int id;
    private String tipoRegistro;
    private String idProducto; // Puede ser null si es una alerta
    private Integer idSensor;  // Puede ser null si es entrada o salida de producto
    private Float medicion;    // Puede ser null si es entrada o salida de producto
    private Timestamp fecha;

    // Constructor
    public Registro(int id, String tipoRegistro, String idProducto, Integer idSensor, Float medicion, Timestamp fecha) {
        this.id = id;
        this.tipoRegistro = tipoRegistro;
        this.idProducto = idProducto;
        this.idSensor = idSensor;
        this.medicion = medicion;
        this.fecha = fecha;
    }

    // Método para añadir un registro de entrada o salida de producto
    public static boolean añadirRegistroProducto(String tipo, String rfidTag) {
        ConectionDDBB conector = new ConectionDDBB();
        Connection con = null;
        try {
            con = conector.obtainConnection(true);
            PreparedStatement ps = con.prepareStatement("INSERT INTO registro (tipo_registro, id_producto, fecha) VALUES (?, ?, CURRENT_TIMESTAMP)");
            ps.setString(1, tipo);  // "entrada" o "salida"
            ps.setString(2, rfidTag);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            Logs.error("Error: " + e,"Error: " + e);
            return false;
        } finally {
            if (con != null) {
                conector.closeConnection(con);
            }
        }
    }

    // Método para añadir un registro de alerta
    public static boolean añadirRegistroAlerta(int sensorId, float medicion) {
        ConectionDDBB conector = new ConectionDDBB();
        Connection con = null;
        try {
            con = conector.obtainConnection(true);
            PreparedStatement ps = con.prepareStatement("INSERT INTO registro (tipo_registro, id_sensor, medicion, fecha) VALUES ('alerta', ?, ?, CURRENT_TIMESTAMP)");
            ps.setInt(1, sensorId);
            ps.setFloat(2, medicion);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            Logs.error("Error: " + e, "Error: " + e);
            return false;
        } finally {
            if (con != null) {
                conector.closeConnection(con);
            }
        }
    }
    
    public static Map<Character, Integer> obtenerEstadisticasEntradasPorNutriScore() {
        Map<Character, Integer> estadisticas = new HashMap<>();
        ConectionDDBB conector = new ConectionDDBB();
        Connection con = null;
        try {
            con = conector.obtainConnection(true);
            String sql = "SELECT p.nutri_score, COUNT(*) as cantidad FROM registro r " +
                         "JOIN producto p ON r.id_producto = p.rfid_tag " +
                         "WHERE r.tipo_registro = 'entrada' " +
                         "GROUP BY p.nutri_score";
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                estadisticas.put(rs.getString("nutri_score").charAt(0), rs.getInt("cantidad"));
            }
        } catch (SQLException e) {
            Logs.error("Error: " + e,"Error: " + e);
        } finally {
            if (con != null) {
                conector.closeConnection(con);
            }
        }
        return estadisticas;
    }

    // Método para obtener estadísticas de salidas por nutri-score
    public static Map<Character, Integer> obtenerEstadisticasSalidasPorNutriScore() {
        return obtenerEstadisticasEntradasPorNutriScore(); // Reutiliza el mismo método cambiando el tipo_registro en la consulta SQL a 'salida'
    }

    // Método para obtener el número de alertas por tipo de sensor
    public static Map<String, Integer> obtenerEstadisticasAlertasPorTipoSensor() {
        Map<String, Integer> estadisticas = new HashMap<>();
        ConectionDDBB conector = new ConectionDDBB();
        Connection con = null;
        try {
            con = conector.obtainConnection(true);
            String sql = "SELECT s.tipo, COUNT(*) as cantidad FROM registro r " +
                         "JOIN sensores s ON r.id_sensor = s.id " +
                         "WHERE r.tipo_registro = 'alerta' " +
                         "GROUP BY s.tipo";
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                estadisticas.put(rs.getString("tipo"), rs.getInt("cantidad"));
            }
        } catch (SQLException e) {
            Logs.error("Error: " + e,"Error: " + e);
        } finally {
            if (con != null) {
                conector.closeConnection(con);
            }
        }
        return estadisticas;
    }
}

