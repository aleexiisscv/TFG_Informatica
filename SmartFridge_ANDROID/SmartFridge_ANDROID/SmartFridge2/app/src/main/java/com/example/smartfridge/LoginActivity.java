package com.example.smartfridge;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class LoginActivity extends AppCompatActivity {
    private JSONArray usuarios=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText usernameField = findViewById(R.id.user);
        EditText passwordField = findViewById(R.id.Password);
        Button loginButton = findViewById(R.id.loginButton);

        loginButton.setOnClickListener(v -> {
            String username = usernameField.getText().toString().trim();
            String password = passwordField.getText().toString().trim();
            loadUsuarios();

            // Suponiendo que 'usuarios' es tu JSONArray almacenado
            try {
                boolean credentialsValid = false;

                if(username.equals("testUser")) {
                    // Si es el testUser, simula un inicio de sesión exitoso
                    credentialsValid = true;
                } else {
                    for (int i = 0; i < usuarios.length(); i++) {
                        JSONObject user = usuarios.getJSONObject(i);
                        String email = user.getString("correo");
                        String pass = user.getString("pass");

                        if (email.equalsIgnoreCase(username) && pass.equals(password)) {
                            credentialsValid = true;
                            break;
                        }
                    }
                }

                if (credentialsValid) {
                    // Credenciales válidas, redirigir al dashboard
                    Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
                    startActivity(intent);
                    Toast.makeText(LoginActivity.this, "Logged In Correctly", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    // Credenciales inválidas
                    Toast.makeText(LoginActivity.this, "Invalid username or password", Toast.LENGTH_SHORT).show();
                    // Limpia los campos
                    usernameField.setText("");
                    passwordField.setText("");
                }
            } catch (JSONException e) {
                e.printStackTrace();
                // Manejo del error en caso de problemas con el JSON
                Toast.makeText(LoginActivity.this, "Error processing login information", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void loadUsuarios(){
        String url = "http://192.168.116.180:8080/ServerExampleUbicomp-1.0-SNAPSHOT/databaseAction";
        ServerConnectionThread.clase = "LoginActivity";
        ServerConnectionThread thread = new ServerConnectionThread(this, url);
        try {
            thread.join();
        }catch (InterruptedException e){}
    }

    public void handleJsonResponse(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            if (jsonObject.has("usuarios")) {
                JSONArray jsonUsuarios = jsonObject.getJSONArray("usuarios");
                System.out.println("#########"+jsonUsuarios.toString());
                usuarios = jsonUsuarios;
            }
            // Añade más secciones según sea necesario
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
