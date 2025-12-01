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
import java.util.ArrayList;
import java.util.List;
import Android.Logic.Logs;

public class Sensor {
    private int id;
    private String tipo;
    private float medicion;
    private Timestamp ultLectura;

    // Constructor
    public Sensor(int id, String tipo, float medicion, Timestamp ultLectura) {
        this.id = id;
        this.tipo = tipo;
        this.medicion = medicion;
        this.ultLectura = ultLectura;
    }

    // Getters y Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public float getMedicion() {
        return medicion;
    }

    public void setMedicion(float medicion) {
        this.medicion = medicion;
    }

    public Timestamp getUltLectura() {
        return ultLectura;
    }

    public void setUltLectura(Timestamp ultLectura) {
        this.ultLectura = ultLectura;
    }

    // Método para obtener sensores por tipo
    public static List<Sensor> obtenerSensoresPorTipo(String tipo) {
        List<Sensor> sensores = new ArrayList<>();
        Connection con = null;
        try {
            ConectionDDBB conector = new ConectionDDBB();
            con = conector.obtainConnection(true);
            PreparedStatement ps = con.prepareStatement("SELECT * FROM sensores WHERE tipo = ?");
            ps.setString(1, tipo);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                sensores.add(new Sensor(
                    rs.getInt("id"),
                    rs.getString("tipo"),
                    rs.getFloat("medicion"),
                    rs.getTimestamp("ult_lectura")
                ));
            }
        } catch (SQLException e) {
            Logs.error("Error: " ,e.toString());
        } finally {
            if (con != null) {
                new ConectionDDBB().closeConnection(con);
            }
        }
        return sensores;
    }
    
    public static boolean actualizarUltimaMedicion(int sensorId, float nuevaMedicion) {
        Connection con = null;
        try {
            ConectionDDBB conector = new ConectionDDBB();
            con = conector.obtainConnection(true);
            PreparedStatement ps = con.prepareStatement("UPDATE sensores SET medicion = ?, ult_lectura = CURRENT_TIMESTAMP WHERE id = ?");
            ps.setFloat(1, nuevaMedicion);
            ps.setInt(2, sensorId);
            int affectedRows = ps.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            Logs.error("Error: " , e.toString());
            return false;
        } finally {
            if (con != null) {
                new ConectionDDBB().closeConnection(con);
            }
        }
    }
}

