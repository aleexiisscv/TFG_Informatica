package db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.sql.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import logic.Usuario;
import logic.Producto;
import mqtt.MQTTBroker;
import mqtt.MQTTPublisher;
import mqtt.MQTTSuscriber;
import logic.Log;

public class ConnectionDB {

    public Connection obtainConnection(boolean autoCommit) throws NullPointerException {
        Connection con = null;
        int intentos = 5;
        for (int i = 0; i < intentos; i++) {
            Log.logdb.info("Attempt {} to connect to the database", i);
            try {
                Context ctx = new InitialContext();
                // Get the connection factory configured in Tomcat
                DataSource ds = (DataSource) ctx.lookup("java:/comp/env/jdbc/ubica");

                // Obtiene una conexion
                con = ds.getConnection();
                Calendar calendar = Calendar.getInstance();
                java.sql.Date date = new java.sql.Date(calendar.getTime().getTime());
                Log.logdb.debug("Connection creation. Bd connection identifier: {} obtained in {}", con.toString(), date.toString());
                con.setAutoCommit(autoCommit);
                Log.logdb.info("Conection obtained in the attempt: " + i);
                i = intentos;
            } catch (NamingException ex) {
                Log.logdb.error("Error getting connection while trying: {} = {}", i, ex);
            } catch (SQLException ex) {
                Log.logdb.error("ERROR sql getting connection while trying:{ }= {}", i, ex);
                throw (new NullPointerException("SQL connection is null"));
            }
        }
        return con;
    }

    public void closeTransaction(Connection con) {
        try {
            if (con != null && !con.getAutoCommit()) {
                con.commit();
                Log.logdb.debug("Transaction closed");
            }
        } catch (SQLException ex) {
            Log.logdb.error("Error closing the transaction: ", ex);
        }
    }
    

    public void cancelTransaction(Connection con) {
        try {
            con.rollback();
            Log.logdb.debug("Transaction canceled");
        } catch (SQLException ex) {
            Log.logdb.error("ERROR sql when canceling the transation: {}", ex);
        }
    }

    public void closeConnection(Connection con) {
        try {
            Log.logdb.info("Closing the connection");
            if (null != con) {
                Calendar calendar = Calendar.getInstance();
                java.sql.Date date = new java.sql.Date(calendar.getTime().getTime());
                Log.logdb.debug("Connection closed. Bd connection identifier: {} obtained in {}", con.toString(), date.toString());
                con.close();
            }

            Log.logdb.info("The connection has been closed");
        } catch (SQLException e) {
            Log.logdb.error("ERROR sql closing the connection: {}", e);
            e.printStackTrace();
        }
    }



    //************** CALLS TO THE DATABASE ***************************//
 
    
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
            Log.logdb.info("Productos obtenidos con éxito");
        } catch (SQLException ex) {
            Log.logdb.error("Error retrieving products: ", ex);
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
                Log.logdb.info("Inventario obtenido con éxito");
            } catch (SQLException ex) {
                Log.logdb.error("Error retrieving inventory: ", ex);
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
                Log.logdb.info("Usuarios obtenidos con éxito");
            } catch (SQLException ex) {
                Log.logdb.error("Error retrieving users: ", ex);
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
                Log.logdb.info("Sensores obtenidos con éxito");
            } catch (SQLException ex) {
                Log.logdb.error("Error retrieving sensors: ", ex);
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
                Log.logdb.info("Registros obtenidos con éxito");
            } catch (SQLException ex) {
                Log.logdb.error("Error retrieving records: ", ex);
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
                    Log.logdb.info("Producto añadido con éxito");
                    return true;
                } catch (SQLException e) {
                    Log.logdb.error("Error: " + e);
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
                    Log.logdb.info("Usuario actualizado con éxito");
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
                    Log.logdb.info("Usuario añadido con éxito");
                    return true;
                }
            } catch (SQLException e) {
                Log.logdb.error("Error: " + e);
            }
            return false;
        }
        // Método para añadir un producto al inventario y registrar la operación
        public static boolean añadirProductoInventario(String rfidTag, Connection con) {
        
            try {
                // Consultar plazo de caducidad del producto
                PreparedStatement psProducto = con.prepareStatement("SELECT plazo_caducidad FROM ubicua.producto WHERE rfid_tag = ?");
                psProducto.setString(1, rfidTag);
                ResultSet rsProducto = psProducto.executeQuery();
                if (!rsProducto.next()) {
                    return false;
                }
                int plazoCaducidad = rsProducto.getInt("plazo_caducidad");
                Timestamp fechaCaducidad = new Timestamp(System.currentTimeMillis() + plazoCaducidad * 86400000L);

                // Insertar producto en inventario
                PreparedStatement psInventario = con.prepareStatement("INSERT INTO ubicua.inventario (producto_id, fecha_entrada, fecha_caducidad) VALUES (?, CURRENT_TIMESTAMP, ?)");
                psInventario.setString(1, rfidTag);
                psInventario.setTimestamp(2, fechaCaducidad);
                psInventario.executeUpdate();

                // Registrar la operación en la tabla de registros
                PreparedStatement psRegistro = con.prepareStatement("INSERT INTO ubicua.registro (tipo_registro, id_producto, fecha) VALUES ('entrada', ?, CURRENT_TIMESTAMP)");
                psRegistro.setString(1, rfidTag);
                psRegistro.executeUpdate();

                Log.logdb.info("Producto añadido al inventario con éxito");
                return true;
            } catch (SQLException e) {
                Log.log.error("Error: " + e);
                return false;
            } 
        }
        // Método para eliminar el producto más antiguo del inventario y registrar la operación
    public static boolean eliminarProductoInventario(String rfidTag, Connection con) {
       
        try {
            // Eliminar el producto más antiguo
            PreparedStatement ps = con.prepareStatement("DELETE FROM ubicua.inventario WHERE producto_id = ? ORDER BY fecha_entrada ASC LIMIT 1");
            ps.setString(1, rfidTag);
            int affectedRows = ps.executeUpdate();
            if (affectedRows == 0) {
                Log.logdb.error("No se encontró el producto en el inventario");
                return false;
            }

            // Registrar la operación en la tabla de registros
            PreparedStatement psRegistro = con.prepareStatement("INSERT INTO ubicua.registro (tipo_registro, id_producto, fecha) VALUES ('salida', ?, CURRENT_TIMESTAMP)");
            psRegistro.setString(1, rfidTag);
            psRegistro.executeUpdate();

            Log.logdb.info("Producto eliminado del inventario con éxito");

            return true;
        } catch (SQLException e) {
            Log.log.error("Error: " + e);
            return false;
        }
    }
    // Método para añadir un registro de entrada o salida de producto
    public static boolean añadirProductoRegistro(String tipo, String rfidTag, Connection con) {
        
        try {
            PreparedStatement ps = con.prepareStatement("INSERT INTO ubicua.registro (tipo_registro, id_producto, fecha) VALUES (?, ?, CURRENT_TIMESTAMP)");
            ps.setString(1, tipo);  // "entrada" o "salida"
            ps.setString(2, rfidTag);
            ps.executeUpdate();
            Log.logdb.info("Registro añadido con éxito");
            return true;
        } catch (SQLException e) {
            Log.log.error("Error: " + e);
            return false;
        } 
    }
    // Método para añadir un registro de alerta
    public static boolean añadirAlertaRegistro(int sensorId, float medicion, Connection con) {
        try {
            
            PreparedStatement ps = con.prepareStatement("INSERT INTO registro (tipo_registro, id_sensor, medicion, fecha) VALUES ('alerta', ?, ?, CURRENT_TIMESTAMP)");
            ps.setInt(1, sensorId);
            ps.setFloat(2, medicion);
            ps.executeUpdate();
            Log.logdb.info("Alerta añadida con éxito");
            return true;
        } catch (SQLException e) {
            Log.log.error("Error: " + e);
            return false;
        } 
    
    }
    public static boolean actualizarUltimaMedicion(int sensorId, float nuevaMedicion, Connection con) {
        
        try {
            
            
            PreparedStatement ps = con.prepareStatement("UPDATE ubicua.sensores SET medicion = ?, ult_lectura = CURRENT_TIMESTAMP WHERE id = ?");
            ps.setFloat(1, nuevaMedicion);
            ps.setInt(2, sensorId);
            int affectedRows = ps.executeUpdate();
            Log.logdb.info("Medición actualizada con éxito");
            return affectedRows > 0;
        } catch (SQLException e) {
            Log.log.error("Error: " + e);
            return false;
        }
    }
    public static boolean insertarAnomalia(int sensorId, float medicion, Connection con) {
        
        try {
            
            PreparedStatement ps = con.prepareStatement("INSERT INTO ubicua.registro (tipo_registro, id_sensor, medicion, fecha) VALUES ('anomalia', ?, ?, CURRENT_TIMESTAMP)");
            ps.setInt(1, sensorId);
            ps.setFloat(2, medicion);
            ps.executeUpdate();
            Log.logdb.info("Anomalia añadida con éxito");
            return true;
        } catch (SQLException e) {
            Log.log.error("Error: " + e);
            return false;
        } 
    }

}