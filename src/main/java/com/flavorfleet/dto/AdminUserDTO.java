package com.flavorfleet.dto;

import java.time.LocalDateTime;

public class AdminUserDTO {
    private Long id;
    private String name;
    private String email;
    private String phone;        // ← Added (you passed null as placeholder)
    private String role;         // ← "ROLE_ADMIN" or "ROLE_USER"
    private LocalDateTime lastLogin;

    // Constructor matching your current usage in UserService.java line 156
    public AdminUserDTO(Long id, String name, String email, String phone,
                        String role, LocalDateTime lastLogin) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.lastLogin = lastLogin;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }

    // Optional: Add a nice toString for logging
    @Override
    public String toString() {
        return "AdminUserDTO{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", role='" + role + '\'' +
                ", lastLogin=" + lastLogin +
                '}';
    }
}