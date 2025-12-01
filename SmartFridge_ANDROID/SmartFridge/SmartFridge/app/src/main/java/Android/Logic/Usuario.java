package Android.Logic;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import Android.Database.ConectionDDBB;

public class Usuario {
    private int id;
    private String nombre;
    private String correo;
    private String contraseña;
    private int inventarioId;

    // Constructor
    public Usuario(int id, String nombre, String correo, String contraseña, int inventarioId) {
        this.id = id;
        this.nombre = nombre;
        this.correo = correo;
        this.contraseña = contraseña;
        this.inventarioId = inventarioId;
    }

    // Getters y setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getContraseña() {
        return contraseña;
    }

    public void setContraseña(String contraseña) {
        this.contraseña = contraseña;
    }

    public int getInventarioId() {
        return inventarioId;
    }

    public void setInventarioId(int inventarioId) {
        this.inventarioId = inventarioId;
    }
    public static boolean insertarUsuario(Usuario usuario, Connection con) {
        try {
            // Verificar si el usuario ya existe
            PreparedStatement ps = con.prepareStatement("SELECT 1 FROM ubicua.users WHERE correo = ? AND pass = ?");
            ps.setString(1, usuario.getCorreo());
            ps.setString(2, usuario.getContraseña());
            ResultSet rs = ps.executeQuery();

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
            Logs.error("Error: " ,e.toString());
        }
        return false;
    }

    // Metodo para verificar la existencia del usuario por correo y contraseña
    public static boolean existeUsuario(String correo, String contraseña) {
        Connection con = null;
        try {
            con = ConectionDDBB.obtainConnection(true);
            PreparedStatement ps = con.prepareStatement("SELECT 1 FROM users WHERE correo = ? AND contraseña = ?");
            ps.setString(1, correo);
            ps.setString(2, contraseña); // Asumimos que la contraseña podría estar hasheada y se verifica adecuadamente
            ResultSet rs = ps.executeQuery();
            return rs.next(); // Devuelve true si existe al menos una fila que cumple el criterio
        } catch (SQLException e) {
            System.err.println("SQL Error: " + e.getMessage());
        } finally {
            ConectionDDBB.closeConnection(con);
        }
        return false;
    }
}