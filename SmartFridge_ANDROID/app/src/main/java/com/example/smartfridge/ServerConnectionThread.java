package com.example.smartfridge;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Hilo heredado para conexión HTTP contra los Servlets legacy.
 *
 * ATENCIÓN: Esta clase está siendo eliminada. DashboardActivity, SensorActivity,
 * LoginActivity y RegisterActivity ya han sido migradas a Retrofit. Solo 
 * StatsActivity sigue utilizando este hilo para consultar el servlet legacy 
 * 'databaseAction'.
 */
public class ServerConnectionThread extends Thread {
    private final String urlStr;
    private StatsActivity activityStats = null;
    public static String clase = "";

    public ServerConnectionThread(StatsActivity activity, String url) {
        this.activityStats = activity;
        this.urlStr = url;
        start();
    }

    @Override
    public void run() {
        if (activityStats == null) return;

        String response = null;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            response = convertStreamToString(in);

            if (response != null && "StatsActivity".equals(clase)) {
                activityStats.handleJsonResponse(response);
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
