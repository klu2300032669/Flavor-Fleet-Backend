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
            sn.setScheduleDate(LocalDateTime.parse(scheduleDateStr));
        }

        sn.setStatus("PENDING");
        sentNotificationRepository.save(sn);

        if (sn.getScheduleDate() == null || sn.getScheduleDate().isBefore(LocalDateTime.now())) {
            sendNow(sn);
            sn.setSentAt(LocalDateTime.now());
            sn.setStatus("SENT");
            sentNotificationRepository.save(sn);
        }

        return toSentDTO(sn);
    }

    private void sendNow(SentNotification sn) {
        List<User> users;
        if (sn.getUserIds().isEmpty()) {
            users = userRepository.findAll();
        } else {
            users = userRepository.findAllById(sn.getUserIds());
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
            if ("order".equals(notification.getType()) && user.isEmailOrderUpdates()) {
                sendEmail = true;
            } else if ("promotion".equals(notification.getType()) && user.isEmailPromotions()) {
                sendEmail = true;
            }

            if (sendEmail) {
                try {
                    sendNotificationEmail(user, notification);
                } catch (MessagingException e) {
                    logger.error("Failed to send email notification to {}: {}", user.getEmail(), e.getMessage());
                }
            }
        }

        notificationRepository.saveAll(notifications);
    }

    private void sendNotificationEmail(User user, Notification notification) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(user.getEmail());
        helper.setSubject(notification.getTitle());
        helper.setFrom(System.getenv("EMAIL_FROM"));  // Use env var

        String htmlContent = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>%s</title>
            </head>
            <body>
                <h1>%s</h1>
                %s
                %s
            </body>
            </html>
            """,
            notification.getTitle(),
            notification.getTitle(),
            notification.getContent(),
            notification.getImageUrl() != null ? "<img src=\"" + notification.getImageUrl() + " alt=\"Notification image\">" : ""
        );

        helper.setText(htmlContent, true);
        mailSender.send(message);
        logger.info("Email notification sent to {}", user.getEmail());
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
                .findByStatusAndScheduleDateBefore("PENDING", now);
        
        for (SentNotification sn : pending) {
            sendNow(sn);
            sn.setSentAt(now);
            sn.setStatus("SENT");
            sentNotificationRepository.save(sn);
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
        payload.put("content", "Your order #" + order.getId() + " is now " + newStatus);
        payload.put("type", "order");
        payload.put("userIds", List.of(order.getUser().getId()));
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