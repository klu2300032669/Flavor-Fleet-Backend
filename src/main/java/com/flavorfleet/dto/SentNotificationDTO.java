// dto/SentNotificationDTO.java (no changes)
package com.flavorfleet.dto;

import java.time.LocalDateTime;
import java.util.List;

public class SentNotificationDTO {
    private Long id;
    private String title;
    private String content;
    private String imageUrl;
    private String type;
    private List<Long> userIds;
    private LocalDateTime sentAt;
    private LocalDateTime scheduleDate;
    private String status;

    // Constructors
    public SentNotificationDTO() {}

    public SentNotificationDTO(Long id, String title, String content, String imageUrl, String type, List<Long> userIds, LocalDateTime sentAt, LocalDateTime scheduleDate, String status) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.imageUrl = imageUrl;
        this.type = type;
        this.userIds = userIds;
        this.sentAt = sentAt;
        this.scheduleDate = scheduleDate;
        this.status = status;
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
    public List<Long> getUserIds() { return userIds; }
    public void setUserIds(List<Long> userIds) { this.userIds = userIds; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public LocalDateTime getScheduleDate() { return scheduleDate; }
    public void setScheduleDate(LocalDateTime scheduleDate) { this.scheduleDate = scheduleDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}