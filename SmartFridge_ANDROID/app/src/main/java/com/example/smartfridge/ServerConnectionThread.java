package com.example.smartfridge;

import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ServerConnectionThread extends Thread {
    private final String urlStr;
    private final String tag = "ServerConnectionThread";
    private DashboardActivity activity= null; // Suponiendo que necesitas actualizar la UI
    private SensorActivity activity2=null; // Suponiendo que necesitas actualizar la UI
    private StatsActivity activity3=null; // Suponiendo que necesitas actualizar la UI
    private LoginActivity activity4=null; // Suponiendo que necesitas actualizar la UI
    private RegisterActivity activity5=null; // Suponiendo que necesitas actualizar la UI
    private CreateProductActivity activity6=null; // Suponiendo que necesitas actualizar la UI
    public static String clase = "";

    public ServerConnectionThread(DashboardActivity activity, String url) {
        this.activity = activity;
        this.urlStr = url;
        start();
    }
    public ServerConnectionThread(SensorActivity activity, String url) {
        this.activity2 = activity;
        this.urlStr = url;
        start();
    }
    public ServerConnectionThread(StatsActivity activity, String url) {
        this.activity3 = activity;
        this.urlStr = url;
        start();
    }
    public ServerConnectionThread(LoginActivity activity, String url) {
        this.activity4 = activity;
        this.urlStr = url;
        start();
    }
    public ServerConnectionThread(RegisterActivity activity, String url) {
        this.activity5 = activity;
        this.urlStr = url;
        start();
    }
    public ServerConnectionThread(CreateProductActivity activity, String url) {
        this.activity6 = activity;
        this.urlStr = url;
        start();
    }

    @Override
    public void run() {
        String response =null;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            response = convertStreamToString(in);

            // Pasar la respuesta completa a la actividad
            if (response != null) {
                if (clase.equals("DashboardActivity")) {
                    activity.handleJsonResponse(response);
                }else if(clase.equals("SensorActivity")){
                    activity2.handleJsonResponse(response);

                } else if (clase.equals("StatsActivity")) {
                    activity3.handleJsonResponse(response);
                } else if (clase.equals("DashboardActivity2")) {
                    activity.handleAlertas(response);

                } else if (clase.equals("LoginActivity")) {
                    activity4.handleJsonResponse(response);
                } else if (clase.equals("RegisterActivity")) {
                    activity5.handleJsonResponse(response);
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }





    private String convertStreamToString(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }
}
