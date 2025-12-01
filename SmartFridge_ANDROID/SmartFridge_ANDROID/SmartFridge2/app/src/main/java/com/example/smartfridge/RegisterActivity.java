
package com.example.smartfridge;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.sql.Connection;


import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RegisterActivity extends AppCompatActivity {
    int id = 1; // id para usuario e inventario

    private JSONArray usuarios=null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Ajusta los márgenes para las barras del sistema
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Obtén los CheckBox por sus IDs
        CheckBox checkBox1 = findViewById(R.id.checkBox2);
        CheckBox checkBox2 = findViewById(R.id.checkBox3);

        // Configura el listener para el primer CheckBox
        checkBox1.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                checkBox2.setChecked(false); // Desactiva el segundo CheckBox
            }
        });

        // Configura el listener para el segundo CheckBox
        checkBox2.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                checkBox1.setChecked(false); // Desactiva el primer CheckBox
            }
        });

        // Obtén los EditText por sus IDs
        EditText nameText = findViewById(R.id.NameText);
        EditText passwordText = findViewById(R.id.Password);
        EditText mailText = findViewById(R.id.Mail);

        // Configura los listeners para limpiar el texto al hacer clic
        nameText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && nameText.getText().toString().equals("Name...")) {
                nameText.setText("");
            }
        });

        passwordText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && passwordText.getText().toString().equals("Password...")) {
                passwordText.setText("");
                if (passwordText.length() < 8) {
                    Toast.makeText(this, "La contraseña debe tener al menos 8 caracteres.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        mailText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && mailText.getText().toString().equals("Mail...")) {
                mailText.setText("");
            }
        });

        // Configura el botón de registro
        Button registerButton = findViewById(R.id.registerButton);
        registerButton.setOnClickListener(v -> {
            loadUsuarios();
            String name = nameText.getText().toString();
            String password = passwordText.getText().toString();
            String mail = mailText.getText().toString();
            String checkBoxSelection = checkBox1.isChecked() ? "CheckBox1" : (checkBox2.isChecked() ? "CheckBox2" : "None");

            // Valida los campos
            if (name.isEmpty() || password.isEmpty() || mail.isEmpty() || checkBoxSelection.equals("None")) {
                Toast.makeText(this, "Por favor, complete todos los campos y seleccione un checkbox.", Toast.LENGTH_SHORT).show();
                return;
            }else if (password.length() < 8) {
                Toast.makeText(this, "La contraseña debe tener al menos 8 caracteres.", Toast.LENGTH_SHORT).show();

                // Limpia los campos
                nameText.setText("");
                passwordText.setText("");
                mailText.setText("");
                checkBox1.setChecked(false);
                checkBox2.setChecked(false);
            }else {
                //%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
                //%%%%%%%%%%% Registrar al usuario (nuevo metodo a implementar) %%%%%%%%%%%%%%%%%%%%%%%%%%%
                    try {
                        if (mail.equals("testUser")) {
                            //Codigo para modo testeo#############################################################################
                            Toast.makeText(this, "Usuario registrado con éxito", Toast.LENGTH_SHORT).show();
                            nameText.setText("");
                            passwordText.setText("");
                            mailText.setText("");
                            checkBox1.setChecked(false);
                            checkBox2.setChecked(false);
                        }else {
                            //Connection con = ConectionDDBB.obtainConnection(true); // MIRAR!!!!
                            String maxId = String.valueOf(getMaxUserId()+1);
                            createUser(maxId, name, mail, password, "1");
                            //Usuario usuario = new Usuario(id, name, mail, password, id);
                           // boolean isRegistered = Usuario.insertarUsuario(usuario, con);     // MIRAR!!!
                            //if (isRegistered) {
                                Toast.makeText(this, "Usuario registrado con éxito", Toast.LENGTH_SHORT).show();
                                // Limpia los campos
                                nameText.setText("");
                                passwordText.setText("");
                                mailText.setText("");
                                checkBox1.setChecked(false);
                                checkBox2.setChecked(false);
                                id = id + 1;
                            }/* else {
                                Toast.makeText(this, "Error al registrar usuario", Toast.LENGTH_SHORT).show();
                                // Limpia los campos
                                nameText.setText("");
                                passwordText.setText("");
                                mailText.setText("");
                                checkBox1.setChecked(false);
                                checkBox2.setChecked(false);
                            }}*/
                    } catch (Exception e) {
                        System.out.println(e.toString());
                    }

                    // %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%5
                }
        });
        // Configura el botón de inicio de sesión
        Button loginButton = findViewById(R.id.loginButton);
        loginButton.setOnClickListener(v -> {
            // Cambia a la actividad de inicio de sesión
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });

    }
    private int getMaxUserId() {
        int maxId = 0; // Empieza desde 0 asumiendo que no hay IDs negativos
        try {
            for (int i = 0; i < usuarios.length(); i++) {
                JSONObject usuario = usuarios.getJSONObject(i);
                int currentId = usuario.getInt("id");
                if (currentId > maxId) {
                    maxId = currentId;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return maxId;
    }
    public void createUser(String id, String name, String email, String password, String inventoryId) {
        OkHttpClient client = new OkHttpClient();
        RequestBody formBody = new FormBody.Builder()
                .add("id", id)
                .add("nombre", name)
                .add("correo", email)
                .add("pass", password)
                .add("inventario", inventoryId)
                .build();
        Request request = new Request.Builder()
                .url("http://192.168.116.180:8080/ServerExampleUbicomp-1.0-SNAPSHOT/addUsuario")
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    Log.d("HTTP_POST", "Response from server: " + responseData);
                } else {
                    Log.d("HTTP_POST", "Failed to connect to server");
                }
            }
        });
    }
    private void loadUsuarios(){
        String url = "http://192.168.116.180:8080/ServerExampleUbicomp-1.0-SNAPSHOT/databaseAction";
        ServerConnectionThread.clase = "RegisterActivity";
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
                usuarios = jsonUsuarios;
            }
            // Añade más secciones según sea necesario
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    /*// Clase interna para representar un usuario
    static class User {
        String name;
        String password;
        String telefono;
        String checkBoxSelection;

        public User(String name, String password, String telefono, String checkBoxSelection) {
            this.name = name;
            this.password = password;
            this.telefono = telefono;
            this.checkBoxSelection = checkBoxSelection;
        }
    }*/
}

