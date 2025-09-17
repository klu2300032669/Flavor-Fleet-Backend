package com.flavorfleet.service;

import com.flavorfleet.dto.ContactMessageDTO;
import com.flavorfleet.entity.ContactMessage;
import com.flavorfleet.repository.ContactMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ContactService {

    private static final Logger logger = LoggerFactory.getLogger(ContactService.class);

    private final ContactMessageRepository contactMessageRepository;
    private final JavaMailSender mailSender;

    public ContactService(ContactMessageRepository contactMessageRepository, JavaMailSender mailSender) {
        this.contactMessageRepository = contactMessageRepository;
        this.mailSender = mailSender;
    }

    @Transactional
    public ContactMessageDTO saveContactMessage(ContactMessageDTO contactMessageDTO) {
        try {
            logger.info("Starting to save contact message for email: {}", contactMessageDTO.getEmail());
            ContactMessage message = new ContactMessage();
            message.setFirstName(contactMessageDTO.getFirstName());
            message.setLastName(contactMessageDTO.getLastName());
            message.setEmail(contactMessageDTO.getEmail());
            message.setMessage(contactMessageDTO.getMessage());
            message.setCreatedAt(System.currentTimeMillis());

            logger.debug("Saving contact message to database: {}", message);
            ContactMessage savedMessage = contactMessageRepository.save(message);
            logger.info("Contact message saved successfully with ID: {}", savedMessage.getId());

            // Email sending is disabled for now
            /*
            try {
                logger.debug("Attempting to send admin notification for message ID: {}", savedMessage.getId());
                sendAdminNotification(savedMessage);
            } catch (Exception e) {
                logger.error("Failed to send email notification for contact message ID: {}. Error: {}", 
                             savedMessage.getId(), e.getMessage());
            }
            */

            return convertToDTO(savedMessage);
        } catch (Exception e) {
            logger.error("Failed to save contact message for email: {}. Error: {}", 
                         contactMessageDTO.getEmail(), e.getMessage(), e);
            throw new RuntimeException("Failed to save contact message: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<ContactMessageDTO> getAllContactMessages() {
        logger.info("Fetching all contact messages");
        List<ContactMessage> messages = contactMessageRepository.findAll();
        logger.info("Fetched {} contact messages", messages.size());
        return messages.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteContactMessage(Long id) {
        try {
            logger.info("Deleting contact message with ID: {}", id);
            if (!contactMessageRepository.existsById(id)) {
                logger.error("Contact message with ID: {} not found", id);
                throw new RuntimeException("Contact message not found with ID: " + id);
            }
            contactMessageRepository.deleteById(id);
            logger.info("Contact message with ID: {} deleted successfully", id);
        } catch (Exception e) {
            logger.error("Failed to delete contact message with ID: {}. Error: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to delete contact message: " + e.getMessage(), e);
        }
    }

    private void sendAdminNotification(ContactMessage message) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo("admin@flavorfleet.com");
        email.setSubject("New Contact Message Received");
        email.setText(
                "New message from: " + message.getFirstName() + " " + message.getLastName() + "\n" +
                "Email: " + message.getEmail() + "\n" +
                "Message: " + message.getMessage() + "\n" +
                "Received at: " + new java.util.Date(message.getCreatedAt())
        );
        logger.debug("Sending email to admin: {}", email.getTo());
        mailSender.send(new SimpleMailMessage[]{email});
        logger.info("Email notification sent to admin for contact message ID: {}", message.getId());
    }

    private ContactMessageDTO convertToDTO(ContactMessage message) {
        return new ContactMessageDTO(
                message.getId(),
                message.getFirstName(),
                message.getLastName(),
                message.getEmail(),
                message.getMessage(),
                message.getCreatedAt()
        );
    }
}