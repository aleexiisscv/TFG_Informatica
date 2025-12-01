package Android.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import java.sql.DriverManager;

import javax.sql.DataSource;
import Android.Logic.Logs;

import Android.Logic.Usuario;
import Android.Logic.Producto;
import Android.Mqtt.MQTTBroker;
import Android.Mqtt.MQTTPublisher;
import Android.Mqtt.MQTTSuscriber;

public class ConnectionDB {

    private static final String DB_URL = "jdbc:mysql://<your-database-host>:<port>/<database-name>";
    private static final String USER = "ubicua";
    private static final String PASSWORD = "ubicua";

    public Connection obtainConnection(boolean autoCommit) throws NullPointerException {
        Connection con = null;
        int intentos = 5;

        for (int i = 0; i < intentos; i++) {
            Logs.info("Database", "Attempt " + i + " to connect to the database");
            try {
                // Load MySQL JDBC Driver
                Class.forName("com.mysql.cj.jdbc.Driver");

                // Obtain a connection
                con = DriverManager.getConnection(DB_URL, USER, PASSWORD);
                con.setAutoCommit(autoCommit);

                Logs.info("Database", "Connection obtained on attempt: " + i);

                // Enviar mensaje a MQTT después de ctx.lookup 
                MQTTBroker broker = MQTTBroker.getInstance();
                MQTTSuscriber suscriber = new MQTTSuscriber();
                suscriber.suscribeTopic(broker, "test");
                MQTTPublisher.publish(broker, "test", "look up bn");

                Calendar calendar = Calendar.getInstance();
                java.sql.Date date = new java.sql.Date(calendar.getTime().getTime());
                Logs.debug(con.toString(), date.toString());
                con.setAutoCommit(autoCommit);
                Logs.info("Database", "Conection obtained in the attempt: " + i);
                i = intentos;
                break; // Exit loop on success
            /*} catch (Exception ex) {
                Logs.error("Database", "Error getting connection while trying: {} = {}", String.valueOf(i), ex.toString());
            */
            } catch (SQLException ex) {
                Logs.error("Database", "ERROR sql getting connection while trying:{ }= {}", String.valueOf(i), ex.toString());
                throw (new NullPointerException("SQL connection is null"));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return con;
    }

    public void closeTransaction(Connection con) {
        try {
            con.commit();
            Logs.debug("Database", "Transaction closed");
        } catch (SQLException ex) {
            Logs.error("Error closing the transaction: {}", ex.toString());
        }
    }

    public void cancelTransaction(Connection con) {
        try {
            con.rollback();
            Logs.debug("Database", "Transaction canceled");
        } catch (SQLException ex) {
            Logs.error("ERROR sql when canceling the transation: {}", ex.toString());
        }
    }

    public void closeConnection(Connection con) {
        try {
            Logs.info("Database", "Closing the connection");
            if (null != con) {
                Calendar calendar = Calendar.getInstance();
                java.sql.Date date = new java.sql.Date(calendar.getTime().getTime());
                Logs.debug("Connection closed. Bd connection identifier: {} obtained in {}", con.toString() + " " + date.toString());
                con.close();
            }

            Logs.info("Database", "The connection has been closed");
        } catch (SQLException e) {
            Logs.error("ERROR sql closing the connection: {}", e.toString());
            e.printStackTrace();
        }
    }

    public static PreparedStatement getStatement(Connection con, String sql) {
        PreparedStatement ps = null;
        try {
            if (con != null) {
                ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

            }
        } catch (SQLException ex) {
            Logs.warn("ERROR sql creating PreparedStatement:{} ", ex.toString());
        }

        return ps;
    }

    //************** CALLS TO THE DATABASE ***************************//
    public static PreparedStatement GetStations(Connection con) {
        return getStatement(con, "SELECT * FROM WHEATHERSTATION.STATION");
    }

    public static PreparedStatement GetFilas(Connection con) {
        return getStatement(con, "SELECT * FROM ubicua.producto;");
    }

    //Metodo de ejemplo para obtener productos
    public static List<Map<String, Object>> GetProductos(Connection con) {
        List<Map<String, Object>> productos = new ArrayList<>();
        String sql = "SELECT rfid_tag, nombre, plazo_caducidad, nutri_score FROM ubicua.producto";

        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> producto = new HashMap<>();
                producto.put("rfid_tag", rs.getString("rfid_tag"));
                producto.put("nombre", rs.getString("nombre"));
                producto.put("plazo_caducidad", rs.getInt("plazo_caducidad"));
                producto.put("nutri_score", rs.getString("nutri_score"));
                productos.add(producto);
            }
        } catch (SQLException ex) {
            Logs.error("Error retrieving products: ", ex.toString());
        }
        return productos;
    }

    // Método para obtener inventario
    public static List<Map<String, Object>> GetInventario(Connection con) {
        List<Map<String, Object>> inventario = new ArrayList<>();
        String sql = "SELECT id, producto_id, fecha_entrada, fecha_caducidad FROM ubicua.inventario";

        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", rs.getInt("id"));
                item.put("producto_id", rs.getString("producto_id"));
                item.put("fecha_entrada", rs.getTimestamp("fecha_entrada"));
                item.put("fecha_caducidad", rs.getTimestamp("fecha_caducidad"));
                inventario.add(item);
            }
        } catch (SQLException ex) {
            Logs.error("Error retrieving inventory: ", ex.toString());
        }
        return inventario;
    }

    // Método para obtener usuarios
    public static List<Map<String, Object>> GetUsuarios(Connection con) {
        List<Map<String, Object>> usuarios = new ArrayList<>();
        String sql = "SELECT id, nombre, correo, pass, inventario FROM ubicua.users";

        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("id", rs.getInt("id"));
                user.put("nombre", rs.getString("nombre"));
                user.put("correo", rs.getString("correo"));
                user.put("pass", rs.getString("pass"));
                user.put("inventario", rs.getInt("inventario"));
                usuarios.add(user);
            }
        } catch (SQLException ex) {
            Logs.error("Error retrieving users: ", ex.toString());
        }
        return usuarios;
    }

    // Método para obtener sensores
    public static List<Map<String, Object>> GetSensores(Connection con) {
        List<Map<String, Object>> sensores = new ArrayList<>();
        String sql = "SELECT id, tipo, medicion, ult_lectura FROM ubicua.sensores";

        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> sensor = new HashMap<>();
                sensor.put("id", rs.getInt("id"));
                sensor.put("tipo", rs.getString("tipo"));
                sensor.put("medicion", rs.getFloat("medicion"));
                sensor.put("ult_lectura", rs.getTimestamp("ult_lectura"));
                sensores.add(sensor);
            }
        } catch (SQLException ex) {
            Logs.error("Error retrieving sensors: ", ex.toString());
        }
        return sensores;
    }

    // Método para obtener registros
    public static List<Map<String, Object>> GetRegistros(Connection con) {
        List<Map<String, Object>> registros = new ArrayList<>();
        String sql = "SELECT id, tipo_registro, id_producto, id_sensor, medicion, fecha FROM ubicua.registro";

        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> registro = new HashMap<>();
                registro.put("id", rs.getInt("id"));
                registro.put("tipo_registro", rs.getString("tipo_registro"));
                registro.put("id_producto", rs.getString("id_producto"));
                registro.put("id_sensor", rs.getInt("id_sensor"));
                registro.put("medicion", rs.getFloat("medicion"));
                registro.put("fecha", rs.getTimestamp("fecha"));
                registros.add(registro);
            }
        } catch (SQLException ex) {
            Logs.error("Error retrieving records: ", ex.toString());
        }
        return registros;
    }


    public static boolean insertarProducto(Producto producto, Connection con) {
        try {
            PreparedStatement ps = con.prepareStatement("INSERT INTO ubicua.producto (rfid_tag, nombre, nutri_score, plazo_caducidad) VALUES (?, ?, ?, ?)");
            ps.setString(1, producto.getRfidTag());
            ps.setString(2, producto.getNombre());
            ps.setString(3, String.valueOf(producto.getNutriScore()));
            ps.setInt(4, producto.getPlazoCaducidad());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            Logs.error("Error: ", e.toString());
        }
        return false;
    }

    public static boolean insertarUsuario(Usuario usuario, Connection con) {
        try {
            // Verificar si el usuario ya existe
            PreparedStatement ps = con.prepareStatement("SELECT 1 FROM ubicua.users WHERE correo = ? AND pass = ?");
            ps.setString(1, usuario.getCorreo());
            ps.setString(2, usuario.getContraseña());
            ResultSet rs = ps.executeQuery();
            System.out.println("dff");
            if (rs.next()) {
                // El usuario existe, actualizar sus atributos
                ps = con.prepareStatement("UPDATE ubicua.users SET id=?, nombre = ?, inventario = ? WHERE correo = ? AND pass = ?");
                ps.setInt(1, usuario.getId());
                ps.setString(2, usuario.getNombre());
                ps.setInt(3, usuario.getInventarioId());
                ps.setString(4, usuario.getCorreo());
                ps.setString(5, usuario.getContraseña());
                ps.executeUpdate();
                return true;
            } else {
                // El usuario no existe, insertar un nuevo usuario
                ps = con.prepareStatement("INSERT INTO ubicua.users (id,nombre, correo, pass, inventario) VALUES (?,?, ?, ?, ?)");
                ps.setInt(1, usuario.getId());
                ps.setString(2, usuario.getNombre());
                ps.setString(3, usuario.getCorreo());
                ps.setString(4, usuario.getContraseña());
                ps.setInt(5, usuario.getInventarioId());
                ps.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            Logs.error("Error: ", e.toString());
        }
        return false;
    }
}


