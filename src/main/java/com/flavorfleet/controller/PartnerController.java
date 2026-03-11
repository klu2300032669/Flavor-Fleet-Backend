package com.flavorfleet.controller;

import com.flavorfleet.dto.PartnerApplicationDTO;
import com.flavorfleet.service.PartnerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/partners")
@CrossOrigin(origins = "http://localhost:8484")
public class PartnerController {
    private static final Logger logger = LoggerFactory.getLogger(PartnerController.class);
    private final PartnerService partnerService;

    public PartnerController(PartnerService partnerService) {
        this.partnerService = partnerService;
    }

    @PostMapping("/apply")
    public ResponseEntity<?> applyForPartnership(@Valid @RequestBody PartnerApplicationDTO dto) {
        logger.info("Received partner application from email: {}", dto.getEmail());
        try {
            partnerService.createApplication(dto);
            return ResponseEntity.ok(new AdminController.SuccessResponse("Application submitted successfully. We'll review it soon."));
        } catch (IllegalArgumentException e) {
            logger.warn("Application submission failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new AdminController.ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error submitting application: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AdminController.ErrorResponse("Failed to submit application: " + e.getMessage()));
        }
    }
}