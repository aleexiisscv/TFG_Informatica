package Android.Servlets;

import Android.db.ConnectionDB;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import Android.Logic.Logs;
import Android.Logic.Usuario;

@WebServlet("/addUsuario")
public class UsuarioServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public UsuarioServlet() {
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
            int id = Integer.parseInt(request.getParameter("id"));
            String nombre = request.getParameter("nombre");
            String correo = request.getParameter("correo");
            String contraseña = request.getParameter("pass");
            int inventarioId = Integer.parseInt(request.getParameter("inventario"));

            Usuario usuario = new Usuario(id, nombre, correo, contraseña, inventarioId);
            boolean exito = ConnectionDB.insertarUsuario(usuario, con);
            if (exito) {
                out.println("Usuario añadido o actualizado con éxito.");
            } else {
                out.println("Error al añadir o actualizar el usuario.");
            }
        } else {
            out.println("Failed to obtain a connection to the database.");
        }
        if (con != null) {
            connectionDB.closeConnection(con); // Cerrar la conexión
        }
        out.close();
    }
}
