package com.flavorfleet.dto;

public class LoginResponse {
    private String token;
    private String email;
    private String name;
    private String message;

    public LoginResponse(String token, String email, String name, String message) {
        this.token = token;
        this.email = email;
        this.name = name;
        this.message = message;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}