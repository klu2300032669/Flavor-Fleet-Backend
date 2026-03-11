package com.flavorfleet.service;

import com.flavorfleet.dto.PartnerApplicationDTO;
import com.flavorfleet.entity.PartnerApplication;
import com.flavorfleet.entity.User;
import com.flavorfleet.repository.PartnerApplicationRepository;
import com.flavorfleet.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PartnerService {

    private static final Logger logger = LoggerFactory.getLogger(PartnerService.class);

    private final PartnerApplicationRepository repository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final NotificationService notificationService;
    private final RestaurantService restaurantService; // Required for creating restaurant on approval

    @Value("${spring.mail.from}")
    private String fromEmail;

    public PartnerService(
            PartnerApplicationRepository repository,
            UserService userService,
            UserRepository userRepository,
            JavaMailSender mailSender,
            NotificationService notificationService,
            RestaurantService restaurantService) {
        this.repository = repository;
        this.userService = userService;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.notificationService = notificationService;
        this.restaurantService = restaurantService;
    }

    @Transactional
    public PartnerApplicationDTO createApplication(PartnerApplicationDTO dto) {
        logger.info("Creating partner application for email: {}", dto.getEmail());

        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            logger.warn("Email already exists for partner application: {}", dto.getEmail());
            throw new IllegalArgumentException("Email already registered");
        }

        PartnerApplication app = toEntity(dto);
        PartnerApplication saved = repository.save(app);

        // Notify admin via system notification
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "New Partner Application");
        payload.put("content", "New restaurant application from " + dto.getRestaurantName() + " (" + dto.getEmail() + ")");
        payload.put("type", "system");
        notificationService.sendNotification(payload);

        logger.info("Partner application created successfully with ID: {}", saved.getId());
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<PartnerApplicationDTO> getApplicationsByStatus(String status) {
        logger.info("Fetching partner applications with status: {}", status);

        List<PartnerApplication> apps;
        if ("ALL".equalsIgnoreCase(status)) {
            apps = repository.findAll();
        } else {
            apps = repository.findByStatus(status.toUpperCase());
        }

        return apps.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public void approveApplication(Long id) {
        logger.info("Approving partner application ID: {}", id);

        PartnerApplication app = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found with ID: " + id));

        if (!"PENDING".equals(app.getStatus())) {
            throw new IllegalStateException("Application already processed (status: " + app.getStatus() + ")");
        }

        try {
            // Step 1: Create restaurant owner user account
            User owner = userService.createRestaurantOwner(app.getOwnerName(), app.getEmail());

            // Step 2: Create linked restaurant entity
            restaurantService.createFromApplication(app, owner);

            // Step 3: Update application status
            app.setStatus("APPROVED");
            app.setUpdatedAt(LocalDateTime.now());
            repository.save(app);

            // Step 4: Send success email
            sendApprovalEmail(app);

            logger.info("Successfully approved application ID: {} for restaurant: {}", id, app.getRestaurantName());
        } catch (Exception e) {
            logger.error("Failed to approve application ID: {}", id, e);
            throw new RuntimeException("Approval failed: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void rejectApplication(Long id, String reason) {
        logger.info("Rejecting partner application ID: {} with reason: {}", id, reason);

        PartnerApplication app = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found with ID: " + id));

        if (!"PENDING".equals(app.getStatus())) {
            throw new IllegalStateException("Application already processed (status: " + app.getStatus() + ")");
        }

        app.setStatus("REJECTED");
        app.setUpdatedAt(LocalDateTime.now());
        repository.save(app);

        sendRejectionEmail(app, reason != null && !reason.trim().isEmpty() ? reason : "No reason provided");

        logger.info("Successfully rejected application ID: {}", id);
    }

    // ────────────────────────────────────────────────
    // Entity ↔ DTO Converters
    // ────────────────────────────────────────────────

    private PartnerApplicationDTO toDTO(PartnerApplication app) {
        PartnerApplicationDTO dto = new PartnerApplicationDTO();
        dto.setId(app.getId());
        dto.setRestaurantName(app.getRestaurantName());
        dto.setOwnerName(app.getOwnerName());
        dto.setEmail(app.getEmail());
        dto.setPhone(app.getPhone());
        dto.setCuisineType(app.getCuisineType());
        dto.setAddress(app.getAddress());
        dto.setCity(app.getCity());
        dto.setMessage(app.getMessage());
        dto.setStatus(app.getStatus());
        dto.setCreatedAt(app.getCreatedAt());
        dto.setUpdatedAt(app.getUpdatedAt());
        return dto;
    }

    private PartnerApplication toEntity(PartnerApplicationDTO dto) {
        PartnerApplication app = new PartnerApplication();
        app.setRestaurantName(dto.getRestaurantName());
        app.setOwnerName(dto.getOwnerName());
        app.setEmail(dto.getEmail());
        app.setPhone(dto.getPhone());
        app.setCuisineType(dto.getCuisineType());
        app.setAddress(dto.getAddress());
        app.setCity(dto.getCity());
        app.setMessage(dto.getMessage());
        app.setStatus(dto.getStatus() != null ? dto.getStatus() : "PENDING");
        return app;
    }

    // ────────────────────────────────────────────────
    // Professional Email Templates
    // ────────────────────────────────────────────────

    private void sendApprovalEmail(PartnerApplication app) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(app.getEmail());
            helper.setSubject("Flavor Fleet - Your Partner Application is Approved!");
            helper.setFrom(fromEmail);

            String htmlContent = String.format("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Application Approved - Flavor Fleet</title>
                    <style>
                        body { margin:0; padding:0; background:#f4f4f4; font-family:Arial,sans-serif; }
                        .container { max-width:600px; margin:30px auto; background:#ffffff; border-radius:12px; overflow:hidden; box-shadow:0 4px 20px rgba(0,0,0,0.1); }
                        .header { background:linear-gradient(135deg,#111827,#1f2937); padding:40px 20px; text-align:center; color:#ffffff; }
                        .header h1 { margin:0; font-size:28px; }
                        .content { padding:40px 30px; color:#333333; line-height:1.6; font-size:16px; }
                        .highlight { background:#f9fafb; padding:20px; border-radius:8px; border:1px solid #e5e7eb; margin:20px 0; }
                        .button { display:inline-block; background:#111827; color:#ffffff; padding:12px 30px; text-decoration:none; border-radius:8px; font-weight:600; margin:20px 0; }
                        .footer { background:#f4f4f4; padding:20px; text-align:center; font-size:14px; color:#6b7280; }
                        .footer a { color:#111827; text-decoration:none; font-weight:600; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Congratulations, %s!</h1>
                        </div>
                        <div class="content">
                            <p>We're excited to inform you that your partner application for <strong>%s</strong> has been <strong>approved</strong>!</p>
                            <div class="highlight">
                                <p>Your restaurant owner account has been created successfully.</p>
                                <p>Check your inbox (or spam/junk folder) for the login credentials email with your temporary password.</p>
                                <p>Log in right away and start managing your restaurant on our platform.</p>
                            </div>
                            <p style="text-align:center;">
                                <a href="http://localhost:8484/login" class="button">Log In to Your Dashboard</a>
                            </p>
                            <p>If you have any questions, our partner support team is ready to assist you.</p>
                            <p>Best regards,<br><strong>Flavor Fleet Partner Team</strong></p>
                        </div>
                        <div class="footer">
                            <p>© 2025 Flavor Fleet. All rights reserved.<br>
                            <a href="#">Contact Support</a> | <a href="#">Privacy Policy</a></p>
                        </div>
                    </div>
                </body>
                </html>
                """, app.getOwnerName(), app.getRestaurantName());

            helper.setText(htmlContent, true);
            mailSender.send(message);
            logger.info("Approval email sent successfully to: {}", app.getEmail());
        } catch (MessagingException e) {
            logger.error("Failed to send approval email to {}: {}", app.getEmail(), e.getMessage(), e);
        }
    }

    private void sendRejectionEmail(PartnerApplication app, String reason) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(app.getEmail());
            helper.setSubject("Flavor Fleet - Update on Your Partner Application");
            helper.setFrom(fromEmail);

            String htmlContent = String.format("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Application Update - Flavor Fleet</title>
                    <style>
                        body { margin:0; padding:0; background:#f4f4f4; font-family:Arial,sans-serif; }
                        .container { max-width:600px; margin:30px auto; background:#ffffff; border-radius:12px; overflow:hidden; box-shadow:0 4px 20px rgba(0,0,0,0.1); }
                        .header { background:linear-gradient(135deg,#111827,#1f2937); padding:40px 20px; text-align:center; color:#ffffff; }
                        .header h1 { margin:0; font-size:28px; }
                        .content { padding:40px 30px; color:#333333; line-height:1.6; font-size:16px; }
                        .highlight { background:#fef2f2; padding:20px; border-radius:8px; border:1px solid #fecaca; margin:20px 0; }
                        .footer { background:#f4f4f4; padding:20px; text-align:center; font-size:14px; color:#6b7280; }
                        .footer a { color:#111827; text-decoration:none; font-weight:600; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Application Update</h1>
                        </div>
                        <div class="content">
                            <p>Dear %s,</p>
                            <p>Thank you for your interest in becoming a partner with Flavor Fleet.</p>
                            <p>After careful review, we regret to inform you that your application for <strong>%s</strong> has not been approved at this time.</p>
                            <div class="highlight">
                                <p><strong>Reason:</strong> %s</p>
                            </div>
                            <p>We truly appreciate the time and effort you put into your application. You are welcome to reapply in the future if your circumstances change.</p>
                            <p>If you have any questions or need clarification, please feel free to reach out to our support team.</p>
                            <p>Best regards,<br><strong>Flavor Fleet Partner Team</strong></p>
                        </div>
                        <div class="footer">
                            <p>© 2025 Flavor Fleet. All rights reserved.<br>
                            <a href="#">Contact Support</a> | <a href="#">Privacy Policy</a></p>
                        </div>
                    </div>
                </body>
                </html>
                """, app.getOwnerName(), app.getRestaurantName(), reason);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            logger.info("Rejection email sent successfully to: {}", app.getEmail());
        } catch (MessagingException e) {
            logger.error("Failed to send rejection email to {}: {}", app.getEmail(), e.getMessage(), e);
        }
    }
}