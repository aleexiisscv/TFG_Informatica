package com.example.smartfridge;

import java.util.ArrayList;
import java.util.List;

public class UserManager {
    private static List<User> users = new ArrayList<>();

    // Método para obtener la lista de usuarios
    public static List<User> getUsers() {
        return users;
    }

    // Método para agregar un usuario
    public static void addUser(User user) {
        users.add(user);
    }

    // Método para validar usuario y contraseña
    public static boolean validateUser(String username, String password) {
        for (User user : users) {
            if (user.getName().equals(username) && user.getPassword().equals(password)) {
                return true;
            }
        }
        return false;
    }
}

