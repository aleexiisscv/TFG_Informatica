package servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import servlets.DatabaseServlet;
import logic.Log;

@WebServlet("/cambiarModo")
public class CambiarModoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (DatabaseServlet.modo.equals("INSERTAR")) {
            DatabaseServlet.modo = "ELIMINAR";
            Log.log.info("modo cambiado a: ELIMINAR");
        } else {
            DatabaseServlet.modo = "INSERTAR";
            Log.log.info("modo cambiado a: INSERTAR");
        }
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(DatabaseServlet.modo);
    }
}

