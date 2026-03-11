package com.flavorfleet.service;

import com.flavorfleet.dto.NotificationDTO;
import com.flavorfleet.dto.SentNotificationDTO;
import com.flavorfleet.entity.Notification;
import com.flavorfleet.entity.Order;
import com.flavorfleet.entity.SentNotification;
import com.flavorfleet.entity.User;
import com.flavorfleet.repository.NotificationRepository;
import com.flavorfleet.repository.SentNotificationRepository;
import com.flavorfleet.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
@EnableScheduling
public class NotificationService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30 minutes

    private final NotificationRepository notificationRepository;
    private final SentNotificationRepository sentNotificationRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    public NotificationService(NotificationRepository notificationRepository,
                               SentNotificationRepository sentNotificationRepository,
                               UserRepository userRepository,
                               JavaMailSender mailSender,
                               @Lazy UserService userService) {
        this.notificationRepository = notificationRepository;
        this.sentNotificationRepository = sentNotificationRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public Page<NotificationDTO> getNotifications(User user, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByUserOrderBySentAtDesc(user, pageable);
        return page.map(this::toDTO);
    }

    @Transactional
    public void markAsRead(Long id, User user) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        if (!n.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Unauthorized");
        }
        n.setRead(true);
        notificationRepository.save(n);
    }

    @Transactional
    public void markAllRead(User user) {
        List<Notification> ns = notificationRepository.findByUserAndIsReadFalse(user);
        ns.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(ns);
    }

    @Transactional
    public void deleteNotification(Long id, User user) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        if (!n.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Unauthorized");
        }
        notificationRepository.delete(n);
    }

    @Transactional
    public void clearAll(User user) {
        notificationRepository.deleteByUser(user);
    }

    @Transactional
    public void updatePreferences(User user, Map<String, Boolean> prefs) {
        user.setEmailOrderUpdates(prefs.getOrDefault("emailOrderUpdates", user.isEmailOrderUpdates()));
        user.setEmailPromotions(prefs.getOrDefault("emailPromotions", user.isEmailPromotions()));
        user.setDesktopNotifications(prefs.getOrDefault("desktopNotifications", user.isDesktopNotifications()));
        userRepository.save(user);
    }

    @Transactional
    public SentNotificationDTO sendNotification(Map<String, Object> payload) {
        SentNotification sn = new SentNotification();
        sn.setTitle((String) payload.get("title"));
        sn.setContent((String) payload.get("content"));
        sn.setImageUrl((String) payload.get("imageUrl"));
        sn.setType((String) payload.get("type"));

        // Safely convert userIds from List<Object> (containing Integer) to List<Long>
        @SuppressWarnings("unchecked")
        List<Object> rawUserIds = (List<Object>) payload.get("userIds");
        List<Long> userIds = rawUserIds != null
                ? rawUserIds.stream()
                    .map(id -> id instanceof Integer ? ((Integer) id).longValue() : (Long) id)
                    .collect(Collectors.toList())
                : new ArrayList<>();
        sn.setUserIds(userIds);

        String scheduleDateStr = (String) payload.get("scheduleDate");
        if (scheduleDateStr != null && !scheduleDateStr.isEmpty()) {
            try {
                sn.setScheduleDate(LocalDateTime.parse(scheduleDateStr));
            } catch (Exception e) {
                logger.error("Failed to parse schedule date: {}", scheduleDateStr, e);
                sn.setScheduleDate(null);
            }
        }

        sn.setStatus("PENDING");
        sentNotificationRepository.save(sn);

        // If no schedule date or date is in past, send immediately
        if (sn.getScheduleDate() == null || sn.getScheduleDate().isBefore(LocalDateTime.now())) {
            sendNow(sn);
            sn.setSentAt(LocalDateTime.now());
            sn.setStatus("SENT");
            sentNotificationRepository.save(sn);
        } else {
            sn.setStatus("SCHEDULED");
            sentNotificationRepository.save(sn);
            logger.info("Notification scheduled for: {}", sn.getScheduleDate());
        }

        return toSentDTO(sn);
    }

    private void sendNow(SentNotification sn) {
        List<User> users;
        if (sn.getUserIds().isEmpty()) {
            users = userRepository.findAll();
            logger.info("Sending notification to ALL users ({} users)", users.size());
        } else {
            users = userRepository.findAllById(sn.getUserIds());
            logger.info("Sending notification to specific users: {}", sn.getUserIds());
        }

        List<Notification> notifications = new ArrayList<>();
        for (User user : users) {
            Notification notification = new Notification();
            notification.setUser(user);
            notification.setTitle(sn.getTitle());
            notification.setContent(sn.getContent());
            notification.setImageUrl(sn.getImageUrl());
            notification.setType(sn.getType());
            notification.setSentAt(LocalDateTime.now());
            notification.setRead(false);
            notifications.add(notification);

            // Send via SSE if desktop enabled
            if (user.isDesktopNotifications()) {
                sendSseNotification(user.getId(), toDTO(notification));
            }

            // Send email if preferred
            boolean sendEmail = false;
            String emailReason = "";
            if ("order".equals(notification.getType()) && user.isEmailOrderUpdates()) {
                sendEmail = true;
                emailReason = "order updates";
            } else if ("promotion".equals(notification.getType()) && user.isEmailPromotions()) {
                sendEmail = true;
                emailReason = "promotions";
            } else if ("system".equals(notification.getType())) {
                sendEmail = true; // Always send system alerts
                emailReason = "system alert";
            }

            if (sendEmail) {
                try {
                    sendNotificationEmail(user, notification);
                    logger.info("Email sent to {} for {} notification", user.getEmail(), emailReason);
                } catch (MessagingException e) {
                    logger.error("Failed to send email notification to {}: {}", user.getEmail(), e.getMessage());
                } catch (Exception e) {
                    logger.error("Unexpected error sending email to {}: {}", user.getEmail(), e.getMessage());
                }
            } else {
                logger.info("Email not sent to {} - preferences: orderUpdates={}, promotions={}", 
                    user.getEmail(), user.isEmailOrderUpdates(), user.isEmailPromotions());
            }
        }

        notificationRepository.saveAll(notifications);
        logger.info("Created {} notifications for sent notification ID: {}", notifications.size(), sn.getId());
    }

    private void sendNotificationEmail(User user, Notification notification) throws MessagingException {
        if (mailSender == null) {
            logger.error("JavaMailSender is null! Check mail configuration");
            return;
        }

        String fromEmail = System.getenv("EMAIL_FROM");
        if (fromEmail == null || fromEmail.isEmpty()) {
            fromEmail = System.getProperty("EMAIL_FROM");
            if (fromEmail == null || fromEmail.isEmpty()) {
                logger.error("EMAIL_FROM environment variable is not set!");
                return;
            }
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(user.getEmail());
        helper.setSubject(notification.getTitle());
        helper.setFrom(fromEmail);

        // Create HTML content
        String htmlContent = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
                <style>
                    body { 
                        font-family: Arial, sans-serif; 
                        line-height: 1.6; 
                        color: #333; 
                        margin: 0; 
                        padding: 20px;
                        background-color: #f4f4f4;
                    }
                    .container { 
                        max-width: 600px; 
                        margin: 0 auto; 
                        background: white; 
                        padding: 20px; 
                        border-radius: 5px; 
                        box-shadow: 0 2px 5px rgba(0,0,0,0.1);
                    }
                    .header { 
                        background: linear-gradient(120deg, #2c3e50, #e74c3c, #f1c40f);
                        color: white; 
                        padding: 20px; 
                        text-align: center; 
                        border-radius: 5px 5px 0 0;
                    }
                    .content { 
                        padding: 20px; 
                    }
                    .footer { 
                        text-align: center; 
                        margin-top: 20px; 
                        padding-top: 20px; 
                        border-top: 1px solid #eee;
                        color: #666;
                        font-size: 12px;
                    }
                    .notification-type {
                        display: inline-block;
                        padding: 5px 10px;
                        border-radius: 3px;
                        font-size: 12px;
                        font-weight: bold;
                        margin-bottom: 10px;
                    }
                    .order { background: #ffc107; color: #000; }
                    .promotion { background: #28a745; color: white; }
                    .system { background: #dc3545; color: white; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Flavor Fleet</h1>
                        <p>Delicious Food Delivered Fast</p>
                    </div>
                    <div class="content">
                        <div class="notification-type %s">%s</div>
                        <h2>%s</h2>
                        <div>%s</div>
                        %s
                        <p><small>Sent: %s</small></p>
                    </div>
                    <div class="footer">
                        <p>© 2025 Flavor Fleet. All rights reserved.</p>
                        <p>This is an automated message, please do not reply to this email.</p>
                        <p><a href="http://localhost:8484/profile" style="color: #e74c3c;">Manage your notification preferences</a></p>
                    </div>
                </div>
            </body>
            </html>
            """,
            notification.getType() != null ? notification.getType().toLowerCase() : "general",
            notification.getType() != null ? notification.getType().toUpperCase() : "NOTIFICATION",
            notification.getTitle(),
            notification.getContent() != null ? notification.getContent().replace("\n", "<br>") : "",
            notification.getImageUrl() != null ? 
                String.format("<img src='%s' alt='Notification image' style='max-width: 100%%; height: auto; border-radius: 5px; margin: 10px 0;'>", 
                    notification.getImageUrl()) : "",
            LocalDateTime.now().toString()
        );

        helper.setText(htmlContent, true);
        
        try {
            mailSender.send(message);
            logger.info("Email successfully sent to: {}", user.getEmail());
        } catch (Exception e) {
            logger.error("Failed to send email to {}: {}", user.getEmail(), e.getMessage());
            throw e;
        }
    }

    private void sendSseNotification(Long userId, NotificationDTO notification) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            List<SseEmitter> deadEmitters = new ArrayList<>();
            
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(objectMapper.writeValueAsString(notification)));
                } catch (IOException | IllegalStateException e) {
                    deadEmitters.add(emitter);
                    logger.warn("SSE connection failed for user {}: {}", userId, e.getMessage());
                }
            }
            
            // Remove dead emitters
            emitters.removeAll(deadEmitters);
            if (emitters.isEmpty()) {
                userEmitters.remove(userId);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<SentNotificationDTO> getHistory() {
        return sentNotificationRepository.findAllByOrderBySentAtDesc()
                .stream()
                .map(this::toSentDTO)
                .collect(Collectors.toList());
    }

    @Scheduled(fixedRate = 60000) // Run every minute
    @Transactional
    public void processScheduledNotifications() {
        LocalDateTime now = LocalDateTime.now();
        List<SentNotification> pending = sentNotificationRepository
                .findPendingScheduled("SCHEDULED", now);
        
        logger.info("Processing scheduled notifications: found {} pending", pending.size());
        
        for (SentNotification sn : pending) {
            sendNow(sn);
            sn.setSentAt(now);
            sn.setStatus("SENT");
            sentNotificationRepository.save(sn);
            logger.info("Sent scheduled notification ID: {}", sn.getId());
        }
    }

    public SseEmitter register(User user) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        
        // Add to user's emitter list
        userEmitters.computeIfAbsent(user.getId(), k -> new CopyOnWriteArrayList<>()).add(emitter);
        
        // Setup completion/timeout/error handlers
        emitter.onCompletion(() -> {
            logger.info("SSE completed for user {}", user.getId());
            removeEmitter(user.getId(), emitter);
        });
        
        emitter.onTimeout(() -> {
            logger.info("SSE timeout for user {}", user.getId());
            removeEmitter(user.getId(), emitter);
        });
        
        emitter.onError(e -> {
            logger.error("SSE error for user {}: {}", user.getId(), e.getMessage());
            removeEmitter(user.getId(), emitter);
        });
        
        // Send initial connection message
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("SSE connection established"));
        } catch (IOException e) {
            logger.error("Failed to send initial SSE message to user {}", user.getId(), e);
        }
        
        return emitter;
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                userEmitters.remove(userId);
            }
        }
    }

    public void sendOrderUpdate(Order order, String newStatus) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "Order Status Update");
        payload.put("content", String.format(
            "Your order #%d is now <strong>%s</strong>. " +
            "Thank you for choosing Flavor Fleet!", 
            order.getId(), newStatus
        ));
        payload.put("type", "order");
        payload.put("userIds", List.of(order.getUser().getId()));
        
        logger.info("Sending order update notification for order #{}", order.getId());
        sendNotification(payload);
    }

    private NotificationDTO toDTO(Notification n) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(n.getId());
        dto.setTitle(n.getTitle());
        dto.setContent(n.getContent());
        dto.setImageUrl(n.getImageUrl());
        dto.setType(n.getType());
        dto.setRead(n.isRead());
        dto.setSentAt(n.getSentAt());
        return dto;
    }

    private SentNotificationDTO toSentDTO(SentNotification sn) {
        SentNotificationDTO dto = new SentNotificationDTO();
        dto.setId(sn.getId());
        dto.setTitle(sn.getTitle());
        dto.setContent(sn.getContent());
        dto.setImageUrl(sn.getImageUrl());
        dto.setType(sn.getType());
        dto.setUserIds(sn.getUserIds());
        dto.setSentAt(sn.getSentAt());
        dto.setScheduleDate(sn.getScheduleDate());
        dto.setStatus(sn.getStatus());
        return dto;
    }

    public UserService getUserService() {
        return userService;
    }
}