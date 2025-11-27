package com.flavorfleet.controller;

import com.flavorfleet.config.JwtUtil;  // Import JwtUtil
import com.flavorfleet.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:8484")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    private final ChatService chatService;
    private final JwtUtil jwtUtil;  // Injected

    public ChatController(ChatService chatService, JwtUtil jwtUtil) {  // Added JwtUtil
        this.chatService = chatService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public ResponseEntity<?> chat(@RequestBody String userMessage, HttpServletRequest request) {
        try {
            String token = extractToken(request);
            String email = jwtUtil.getEmailFromToken(token);  // Now injected
            if (email == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Invalid token: Email not found"));
            }
            logger.info("Chat request from email: {} message: {}", email, userMessage);
            String response = chatService.getGrokResponse(userMessage, email);
            return ResponseEntity.ok(new SuccessResponse(response));  // Wrap in SuccessResponse
        } catch (IllegalArgumentException e) {
            logger.warn("Auth error in chat: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Unauthorized: Invalid token"));
        } catch (Exception e) {
            logger.error("Chat failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Chat failed: " + e.getMessage()));
        }
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Invalid or missing Authorization header");
        }
        return authHeader.substring(7);
    }

    // Reuse from AdminController
    public static class ErrorResponse {
        private String message;

        public ErrorResponse(String message) {
            this.message = message;
        }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class SuccessResponse {
        private String message;

        public SuccessResponse(String message) {
            this.message = message;
        }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}