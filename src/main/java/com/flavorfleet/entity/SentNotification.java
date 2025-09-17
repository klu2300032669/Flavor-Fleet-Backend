// entity/SentNotification.java (no changes)
package com.flavorfleet.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sent_notifications")
public class SentNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String imageUrl;

    private String type;

    @ElementCollection
    @CollectionTable(name = "sent_notification_users", joinColumns = @JoinColumn(name = "sent_notification_id"))
    @Column(name = "user_id")
    private List<Long> userIds = new ArrayList<>();

    private LocalDateTime sentAt;

    private LocalDateTime scheduleDate;

    private String status = "PENDING";

    public SentNotification() {}

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