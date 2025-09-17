

package com.flavorfleet.controller;

import com.flavorfleet.dto.ContactMessageDTO;
import com.flavorfleet.service.ContactService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:8484", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE}, allowedHeaders = "*")
public class ContactController {

    private static final Logger logger = LoggerFactory.getLogger(ContactController.class);

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping("/contact")
    public ResponseEntity<?> submitContactMessage(@RequestBody ContactMessageDTO contactMessageDTO) {
        try {
            logger.info("Received contact message from: {}", contactMessageDTO.getEmail());
            ContactMessageDTO savedMessage = contactService.saveContactMessage(contactMessageDTO);
            return ResponseEntity.ok(savedMessage);
        } catch (Exception e) {
            logger.error("Error processing contact message: {}", e.getMessage());
            return ResponseEntity.status(500).body(new ErrorResponse("Failed to submit contact message: " + e.getMessage()));
        }
    }

    @GetMapping("/admin/contact")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<List<ContactMessageDTO>> getAllContactMessages(HttpServletRequest request) {
        logger.info("Admin fetching all contact messages");
        List<ContactMessageDTO> messages = contactService.getAllContactMessages();
        return ResponseEntity.ok(messages);
    }

    @DeleteMapping("/admin/contact/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> deleteContactMessage(@PathVariable Long id) {
        try {
            logger.info("Admin deleting contact message with ID: {}", id);
            contactService.deleteContactMessage(id);
            return ResponseEntity.ok(new SuccessResponse("Contact message deleted successfully"));
        } catch (Exception e) {
            logger.error("Error deleting contact message with ID: {}. Error: {}", id, e.getMessage());
            return ResponseEntity.status(500).body(new ErrorResponse("Failed to delete contact message: " + e.getMessage()));
        }
    }
}

class ErrorResponse {
    private String message;

    public ErrorResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

class SuccessResponse {
    private String message;

    public SuccessResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
