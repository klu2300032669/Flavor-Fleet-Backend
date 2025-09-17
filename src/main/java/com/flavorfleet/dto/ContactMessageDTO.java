package com.flavorfleet.dto;

public class ContactMessageDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String message;
    private Long createdAt;

    // Constructors
    public ContactMessageDTO() {}

    public ContactMessageDTO(Long id, String firstName, String lastName, String email, String message, Long createdAt) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.message = message;
        this.createdAt = createdAt;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}