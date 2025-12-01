package Android.Servlets;

import java.io.IOException;
import java.io.PrintWriter;


import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import Android.Logic.Logs;
import Android.Mqtt.MQTTBroker;
import Android.Mqtt.MQTTPublisher;
import Android.Mqtt.MQTTSuscriber;
@WebServlet("/logButton")
public class LogServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public LogServlet() {
        super();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Logs.info("Database:","Button clicked!");
        MQTTBroker broker = MQTTBroker.getInstance(); 
        MQTTSuscriber suscriber = new MQTTSuscriber(); 
        suscriber.suscribeTopic(broker, "test"); 
        MQTTPublisher.publish(broker, "test", "dan da dan"); 
        response.setContentType("text/html;charset=UTF-8"); 
        PrintWriter out = response.getWriter(); 
        try { 
            out.println("Log message sent successfully!");
        } finally { 
            out.close(); 
        } 
    }
}
