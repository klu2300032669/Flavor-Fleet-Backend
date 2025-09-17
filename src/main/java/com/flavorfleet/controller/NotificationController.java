package com.flavorfleet.controller;

import com.flavorfleet.config.JwtUtil;
import com.flavorfleet.dto.NotificationDTO;
import com.flavorfleet.entity.User;
import com.flavorfleet.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "http://localhost:8484")
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtUtil jwtUtil;

    public NotificationController(NotificationService notificationService, JwtUtil jwtUtil) {
        this.notificationService = notificationService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public Page<NotificationDTO> getNotifications(Pageable pageable) {
        User user = getCurrentUser();
        return notificationService.getNotifications(user, pageable);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        User user = getCurrentUser();
        notificationService.markAsRead(id, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Void> markAllRead() {
        User user = getCurrentUser();
        notificationService.markAllRead(user);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        User user = getCurrentUser();
        notificationService.deleteNotification(id, user);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/clear-all")
    public ResponseEntity<Void> clearAll() {
        User user = getCurrentUser();
        notificationService.clearAll(user);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/preferences")
    public ResponseEntity<Void> updatePreferences(@RequestBody Map<String, Boolean> prefs) {
        User user = getCurrentUser();
        notificationService.updatePreferences(user, prefs);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/sse")
    public SseEmitter sse(@RequestParam("token") String token) {
        String email = jwtUtil.getEmailFromToken(token);
        if (email == null || !jwtUtil.validateToken(token)) {
            throw new SecurityException("Invalid token for SSE");
        }
        User user = notificationService.getUserService().findByEmail(email);
        if (user == null) {
            throw new SecurityException("User not found for SSE");
        }
        return notificationService.register(user);
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        return notificationService.getUserService().findByEmail(email);
    }
}