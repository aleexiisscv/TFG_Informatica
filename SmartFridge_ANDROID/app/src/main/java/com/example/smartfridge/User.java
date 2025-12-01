package com.example.smartfridge;

public class User {
    private String name;
    private String password;
    private String phone;
    private String userType;

    public User(String name, String password, String phone, String userType) {
        this.name = name;
        this.password = password;
        this.phone = phone;
        this.userType = userType;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public String getPhone() {
        return phone;
    }

    public String getUserType() {
        return userType;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }
}

