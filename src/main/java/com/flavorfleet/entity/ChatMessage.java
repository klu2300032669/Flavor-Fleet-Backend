package com.flavorfleet.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;  // From JWT (e.g., email)

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;  // User input

    @Column(nullable = false, columnDefinition = "TEXT")
    private String response;  // AI reply

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    // Constructors
    public ChatMessage() {}

    public ChatMessage(String userId, String message, String response) {
        this.userId = userId;
        this.message = message;
        this.response = response;
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}