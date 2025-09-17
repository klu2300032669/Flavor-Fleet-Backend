// dto/NotificationDTO.java (no changes)
package com.flavorfleet.dto;

import java.time.LocalDateTime;

public class NotificationDTO {
    private Long id;
    private String title;
    private String content;
    private String imageUrl;
    private String type;
    private boolean read;
    private LocalDateTime sentAt;

    // Constructors
    public NotificationDTO() {}
    
    public NotificationDTO(Long id, String title, String content, String imageUrl, 
                          String type, boolean read, LocalDateTime sentAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.imageUrl = imageUrl;
        this.type = type;
        this.read = read;
        this.sentAt = sentAt;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
}