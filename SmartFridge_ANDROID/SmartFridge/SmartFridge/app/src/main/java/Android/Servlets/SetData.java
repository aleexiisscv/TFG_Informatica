package Android.Servlets;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import Android.Logic.Logs;
import Android.Logic.Logic;

@WebServlet("/SetData")
public class SetData extends HttpServlet 
{
	private static final long serialVersionUID = 1L;
       
    public SetData(){
        super();
    }

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		Logs.info("Database: ","--Get values from the DB--");
		response.setContentType("text/html;charset=UTF-8");
		PrintWriter out = response.getWriter();
		try {
			int value = Integer.parseInt(request.getParameter("value"));
			//Logic.setDataToDB(value);
		} catch (NumberFormatException nfe){
			out.println("-1");
			Logs.error("Number Format Exception: ", nfe.toString());
		} catch (IndexOutOfBoundsException iobe) {
			out.println("-1");
			Logs.error("Index out of bounds Exception: " ,iobe.toString());
		} catch (Exception e){
			out.println("-1");
			Logs.error("Exception: " ,e.toString());
		} finally{
			out.close();
		}
	}
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

}
