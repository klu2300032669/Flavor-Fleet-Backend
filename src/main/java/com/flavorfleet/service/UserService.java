package com.flavorfleet.service;

import com.flavorfleet.entity.Address;
import com.flavorfleet.entity.CartItem;
import com.flavorfleet.entity.FavoriteItem;
import com.flavorfleet.entity.Order;
import com.flavorfleet.entity.User;
import com.flavorfleet.repository.AddressRepository;
import com.flavorfleet.repository.CartItemRepository;
import com.flavorfleet.repository.FavoriteItemRepository;
import com.flavorfleet.repository.NotificationRepository;
import com.flavorfleet.repository.OrderRepository;
import com.flavorfleet.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class UserService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final Map<String, Long> OTP_TIMESTAMP = new ConcurrentHashMap<>();
    private static final Map<String, String> otpStore = new ConcurrentHashMap<>();
    private static final Map<String, User> pendingRegistrations = new ConcurrentHashMap<>();

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final CartItemRepository cartItemRepository;
    private final FavoriteItemRepository favoriteItemRepository;
    private final OrderRepository orderRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final NotificationService notificationService;

    @Value("${spring.mail.from}")
    private String fromEmail;

    public UserService(UserRepository userRepository, AddressRepository addressRepository,
                       CartItemRepository cartItemRepository, FavoriteItemRepository favoriteItemRepository,
                       OrderRepository orderRepository, NotificationRepository notificationRepository,
                       PasswordEncoder passwordEncoder, JavaMailSender mailSender,
                       @Lazy NotificationService notificationService) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.cartItemRepository = cartItemRepository;
        this.favoriteItemRepository = favoriteItemRepository;
        this.orderRepository = orderRepository;
        this.notificationRepository = notificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.notificationService = notificationService;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        logger.debug("Loading user by email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        logger.debug("User found: {}. Encoded password: {}", user.getEmail(), user.getPassword());
        String role = user.getRole() != null ? user.getRole() : "ROLE_USER";
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                true, true, true, true,
                Collections.singletonList(new SimpleGrantedAuthority(role))
        );
    }

    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    @Transactional
    public User save(User user) {
        User savedUser = userRepository.save(user);
        userRepository.flush();
        logger.info("User saved successfully with email: {}. Role: {}", user.getEmail(), user.getRole());
        return savedUser;
    }

    @Transactional
    public void deleteAddress(Address address) {
        addressRepository.delete(address);
        logger.info("Address deleted successfully with ID: {}", address.getId());
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        logger.info("Fetching all users from repository");
        return userRepository.findAll();
    }

    @Transactional
    public boolean updateUser(Long id, String role) {
        logger.info("Updating user with ID: {} to role: {}", id, role);
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            logger.warn("User not found with ID: {}", id);
            return false;
        }
        user.setRole("ROLE_" + role.toUpperCase());
        userRepository.save(user);
        logger.info("User role updated successfully for ID: {}", id);
        return true;
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        logger.info("Fetching all orders from repository");
        return orderRepository.findAll();
    }

    @Transactional
    public boolean updateOrderStatus(Long id, String status) {
        logger.info("Updating order status for order ID: {} to status: {}", id, status);
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            logger.warn("Order not found with ID: {}", id);
            return false;
        }
        order.setStatus(status);
        orderRepository.save(order);
        notificationService.sendOrderUpdate(order, status);
        logger.info("Order status updated successfully for ID: {}", id);
        return true;
    }

    @Transactional
    public boolean deleteUser(Long id) {
        logger.info("Attempting to delete user with ID: {}", id);

        if (!userRepository.existsById(id)) {
            logger.warn("User not found with ID: {}", id);
            return false;
        }
        try {
            // Delete associated data
            orderRepository.deleteByUserId(id);
            logger.debug("Deleted orders for user ID: {}", id);
            cartItemRepository.deleteByUserId(id);
            logger.debug("Deleted cart items for user ID: {}", id);
            favoriteItemRepository.deleteByUserId(id);
            logger.debug("Deleted favorite items for user ID: {}", id);
            addressRepository.deleteByUserId(id);
            logger.debug("Deleted addresses for user ID: {}", id);
            notificationRepository.deleteByUserId(id);
            logger.debug("Deleted notifications for user ID: {}", id);
            userRepository.deleteById(id);
            logger.info("User deleted successfully with ID: {}", id);
            return true;
        } catch (Exception e) {
            logger.error("Failed to delete user with ID: {}", id, e);
            throw new RuntimeException("Failed to delete user: " + e.getMessage());
        }
    }

    @Transactional
    public boolean changePassword(String email, String currentPassword, String newPassword) {
        logger.debug("Attempting to change password for email: {}", email);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            logger.warn("User not found for email: {}", email);
            return false;
        }
        logger.debug("User found for email: {}", email);

        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            logger.warn("Current password verification failed for email: {}", email);
            return false;
        }

        // Validate new password complexity
        String passwordRegex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";
        if (!Pattern.matches(passwordRegex, newPassword)) {
            logger.warn("New password does not meet complexity requirements for email: {}", email);
            throw new IllegalArgumentException(
                "New password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one digit, and one special character (@$!%*?&)"
            );
        }

        // Encode and set new password
        String encodedNewPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedNewPassword);
        userRepository.save(user);
        userRepository.flush();
        logger.info("Password changed successfully for email: {}", email);
        return true;
    }

    @Transactional
    public boolean sendOtpForSignup(String email, User user) {
        User existingUser = userRepository.findByEmail(email).orElse(null);
        if (existingUser != null) {
            logger.warn("Email already exists: {}", email);
            return false;
        }

        if (email.equalsIgnoreCase("saketh.surubhotla@gmail.com") || email.equalsIgnoreCase("suthapallichakradhar@gmail.com")) {
            user.setRole("ROLE_ADMIN");
            logger.info("Assigned ROLE_ADMIN to email: {}", email);
        } else {
            user.setRole("ROLE_USER");
            logger.info("Assigned ROLE_USER to email: {}", email);
        }

        String otp = generateOtp();
        otpStore.put(email, otp);
        OTP_TIMESTAMP.put(email, System.currentTimeMillis());
        pendingRegistrations.put(email, user);

        try {
            sendEmail(email, user.getName(), otp, "Flavor Fleet - Your Verification Code", "signup");
            logger.info("Signup OTP email sent successfully to: {}", email);
        } catch (MessagingException e) {
            logger.error("Failed to send signup OTP email to {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Failed to send signup OTP email", e);
        }

        scheduleOtpCleanup(email);
        return true;
    }

    @Transactional
    public User verifySignupOtp(String email, String otp) {
        if (!otpStore.containsKey(email) || !otpStore.get(email).equals(otp) || !pendingRegistrations.containsKey(email)) {
            logger.warn("Invalid OTP or email for signup: {}", email);
            return null;
        }

        Long otpTimestamp = OTP_TIMESTAMP.get(email);
        if (otpTimestamp == null || System.currentTimeMillis() > otpTimestamp + OTP_EXPIRY_MINUTES * 60 * 1000) {
            logger.warn("OTP expired for email: {}", email);
            otpStore.remove(email);
            OTP_TIMESTAMP.remove(email);
            pendingRegistrations.remove(email);
            return null;
        }

        User user = pendingRegistrations.get(email);
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        logger.debug("Encoding password during signup for email: {}. Encoded password: {}", email, encodedPassword);
        user.setPassword(encodedPassword);
        User savedUser = userRepository.save(user);
        userRepository.flush();
        sendWelcomeEmail(savedUser.getEmail(), savedUser.getName());
        otpStore.remove(email);
        OTP_TIMESTAMP.remove(email);
        pendingRegistrations.remove(email);
        logger.info("User registered successfully with email: {}. Role: {}", email, savedUser.getRole());
        return savedUser;
    }

    private void sendWelcomeEmail(String email, String name) {
        try {
            sendEmail(email, name, null, "Welcome to Flavor Fleet - Let’s Embark Together", "welcome");
            logger.info("Welcome email sent successfully to: {}", email);
        } catch (MessagingException e) {
            logger.error("Failed to send welcome email to {}: {}", email, e.getMessage(), e);
        }
    }

    @Transactional
    public boolean sendOtpForPasswordReset(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            logger.warn("User not found for email: {}", email);
            return false;
        }

        String otp = generateOtp();
        otpStore.put(email, otp);
        OTP_TIMESTAMP.put(email, System.currentTimeMillis());

        try {
            sendEmail(email, user.getName(), otp, "Flavor Fleet - Password Reset Code", "reset");
            logger.info("OTP email sent successfully to: {}", email);
        } catch (MessagingException e) {
            logger.error("Failed to send OTP email to {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Failed to send OTP email", e);
        }

        scheduleOtpCleanup(email);
        return true;
    }

    @Transactional
    public boolean resetPassword(String email, String otp, String newPassword) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !otpStore.containsKey(email) || !otpStore.get(email).equals(otp)) {
            logger.warn("Invalid OTP or email: {}", email);
            return false;
        }

        Long otpTimestamp = OTP_TIMESTAMP.get(email);
        if (otpTimestamp == null || System.currentTimeMillis() > otpTimestamp + OTP_EXPIRY_MINUTES * 60 * 1000) {
            logger.warn("OTP expired for email: {}", email);
            otpStore.remove(email);
            OTP_TIMESTAMP.remove(email);
            return false;
        }

        String encodedNewPassword = passwordEncoder.encode(newPassword);
        logger.debug("Encoding new password during reset for email: {}", email);
        user.setPassword(encodedNewPassword);
        userRepository.save(user);
        userRepository.flush();
        otpStore.remove(email);
        OTP_TIMESTAMP.remove(email);
        logger.info("Password reset successful for email: {}", email);
        return true;
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    private void sendEmail(String email, String name, String otp, String subject, String type) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(email);
        helper.setSubject(subject);
        helper.setFrom(fromEmail);

        String htmlContent;
        switch (type) {
            case "signup":
                htmlContent = String.format("""
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body { margin: 0; padding: 0; background: #f9f7f2; font-family: 'Helvetica', sans-serif; }
                            .container { max-width: 720px; margin: 50px auto; background: #fff; border-radius: 20px; overflow: hidden; box-shadow: 0 20px 80px rgba(0,0,0,0.15); }
                            .header { background: linear-gradient(120deg, #2c3e50, #e74c3c, #f1c40f); padding: 50px 30px; text-align: center; color: #fff; position: relative; }
                            .header::before { content: ''; top: -50px; left: -50px; width: 150px; height: 150px; background: rgba(255,255,255,0.1); border-radius: 50%%; }
                            .header h1 { font-size: 38px; margin: 0; font-family: 'Playfair Display', serif; letter-spacing: 1.5px; text-transform: uppercase; position: relative; z-index: 1; white-space: nowrap; }
                            .header p { font-size: 16px; margin: 10px 0 0; opacity: 0.85; position: relative; z-index: 1; }
                            .content { padding: 60px 40px; background: #fff; }
                            .otp-box { background: linear-gradient(135deg, #f1c40f, #e67e22); padding: 20px; text-align: center; font-size: 2.8rem; letter-spacing: 12px; border-radius: 15px; color: #1c2526; font-weight: 900; margin: 30px 0; box-shadow: 0 10px 30px rgba(0,0,0,0.2); font-family: 'Roboto Mono', monospace; transition: transform 0.3s; }
                            .otp-box:hover { transform: scale(1.05); }
                            .greeting { font-size: 26px; color: #2c3e50; margin-bottom: 20px; font-weight: 700; }
                            .text { font-size: 16px; line-height: 1.9; color: #4a4a4a; margin: 20px 0; }
                            .footer { background: #2c3e50; padding: 30px; text-align: center; color: #fff; font-size: 14px; position: relative; }
                            .footer p { margin: 0; text-transform: uppercase; letter-spacing: 1px; }
                            .footer a { color: #f1c40f; text-decoration: none; font-weight: 600; margin-left: 10px; }
                            .divider { height: 3px; background: linear-gradient(to right, transparent, #e67e22, transparent); margin: 35px 0; }
                            @media (max-width: 600px) {
                                .header h1 { font-size: 28px; }
                                .otp-box { font-size: 2rem; letter-spacing: 8px; }
                                .content { padding: 40px 20px; }
                            }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <h1>Flavor Fleet</h1>
                                <p>Discover Culinary Delights</p>
                            </div>
                            <div class="content">
                                <div class="greeting">Greetings %s,</div>
                                <div class="text">Step into the enchanting world of Flavor Fleet! Below is your key to unlock this journey:</div>
                                <div class="otp-box">%s</div>
                                <div class="text">This secret code awaits your use for the next 10 minutes, guarding your entry with care.</div>
                                <div class="divider"></div>
                                <div class="text">Prepare to savor a symphony of flavors that will captivate your senses.</div>
                            </div>
                            <div class="footer">
                                <p>© 2025 Flavor Fleet - All Rights Reserved <a href="#">Contact Us</a></p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """, name, otp);
                break;
            case "welcome":
                htmlContent = String.format("""
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body { margin: 0; padding: 0; background: #f9f7f2; font-family: 'Helvetica', sans-serif; }
                            .container { max-width: 720px; margin: 50px auto; background: #fff; border-radius: 20px; overflow: hidden; box-shadow: 0 20px 80px rgba(0,0,0,0.15); }
                            .header { background: linear-gradient(120deg, #2c3e50, #e74c3c, #f1c40f); padding: 60px 30px; text-align: center; color: #fff; position: relative; }
                            .header::before { content: ''; position: absolute; bottom: -60px; right: -60px; width: 180px; height: 180px; background: rgba(255,255,255,0.1); border-radius: 50%%; }
                            .header h1 { font-size: 42px; margin: 0; font-family: 'Playfair Display', serif; letter-spacing: 2px; text-transform: uppercase; position: relative; z-index: 1; white-space: nowrap; }
                            .header p { font-size: 18px; margin: 15px 0 0; opacity: 0.85; position: relative; z-index: 1; }
                            .content { padding: 60px 40px; background: #fff; }
                            .welcome-box { background: linear-gradient(135deg, #f1c40f, #e67e22); padding: 25px; border-radius: 15px; text-align: center; color: #1c2526; font-size: 2rem; font-weight: 700; margin: 30px 0; box-shadow: 0 10px 30px rgba(0,0,0,0.2); transition: transform 0.3s; }
                            .welcome-box:hover { transform: scale(1.03); }
                            .greeting { font-size: 28px; color: #2c3e50; margin-bottom: 25px; font-weight: 700; }
                            .text { font-size: 16px; line-height: 1.9; color: #4a4a4a; margin: 20px 0; }
                            .footer { background: #2c3e50; padding: 30px; text-align: center; color: #fff; font-size: 14px; position: relative; }
                            .footer p { margin: 0; text-transform: uppercase; letter-spacing: 1px; }
                            .footer a { color: #f1c40f; text-decoration: none; font-weight: 600; margin-left: 10px; }
                            .divider { height: 3px; background: linear-gradient(to right, transparent, #e67e22, transparent); margin: 35px 0; }
                            @media (max-width: 600px) {
                                .header h1 { font-size: 32px; }
                                .welcome-box { font-size: 1.8rem; }
                                .content { padding: 40px 20px; }
                            }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <h1>Flavor Fleet</h1>
                                <p>A Realm of Exquisite Tastes</p>
                            </div>
                            <div class="content">
                                <div class="greeting">Warmest Welcome, %s!</div>
                                <div class="text">Your adventure with Flavor Fleet begins today—a delightful odyssey awaits you.</div>
                                <div class="welcome-box">Account Verified</div>
                                <div class="text">Immerse yourself in a treasure trove of culinary wonders, crafted to enchant your palate.</div>
                                <div class="divider"></div>
                                <div class="text">Should you seek guidance, our devoted team stands ready to assist you.</div>
                            </div>
                            <div class="footer">
                                <p>© 2025 Flavor Fleet - All Rights Reserved <a href="#">Contact Us</a></p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """, name);
                break;
            case "reset":
                htmlContent = String.format("""
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body { margin: 0; padding: 0; background: #f9f7f2; font-family: 'Helvetica', sans-serif; }
                            .container { max-width: 720px; margin: 50px auto; background: #fff; border-radius: 20px; overflow: hidden; box-shadow: 0 20px 80px rgba(0,0,0,0.15); }
                            .header { background: linear-gradient(120deg, #2c3e50, #e74c3c, #f1c40f); padding: 50px 30px; text-align: center; color: #fff; position: relative; }
                            .header::before { content: ''; position: absolute; top: -50px; left: -50px; width: 150px; height: 150px; background: rgba(255,255,255,0.1); border-radius: 50%%; }
                            .header h1 { font-size: 38px; margin: 0; font-family: 'Playfair Display', serif; letter-spacing: 1.5px; text-transform: uppercase; position: relative; z-index: 1; white-space: nowrap; }
                            .header p { font-size: 16px; margin: 10px 0 0; opacity: 0.85; position: relative; z-index: 1; }
                            .content { padding: 60px 40px; background: #fff; }
                            .otp-box { background: linear-gradient(135deg, #f1c40f, #e67e22); padding: 20px; text-align: center; font-size: 2.8rem; letter-spacing: 12px; border-radius: 15px; color: #1c2526; font-weight: 900; margin: 30px 0; box-shadow: 0 10px 30px rgba(0,0,0,0.2); font-family: 'Roboto Mono', monospace; transition: transform 0.3s; }
                            .otp-box:hover { transform: scale(1.05); }
                            .greeting { font-size: 26px; color: #2c3e50; margin-bottom: 20px; font-weight: 700; }
                            .text { font-size: 16px; line-height: 1.9; color: #4a4a4a; margin: 20px 0; }
                            .footer { background: #2c3e50; padding: 30px; text-align: center; color: #fff; font-size: 14px; position: relative; }
                            .footer p { margin: 0; text-transform: uppercase; letter-spacing: 1px; }
                            .footer a { color: #f1c40f; text-decoration: none; font-weight: 600; margin-left: 10px; }
                            .divider { height: 3px; background: linear-gradient(to right, transparent, #e67e22, transparent); margin: 35px 0; }
                            @media (max-width: 600px) {
                                .header h1 { font-size: 28px; }
                                .otp-box { font-size: 2rem; letter-spacing: 8px; }
                                .content { padding: 40px 20px; }
                            }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <h1>Flavor Fleet</h1>
                                <p>Protecting Your Journey</p>
                            </div>
                            <div class="content">
                                <div class="greeting">Dear %s,</div>
                                <div class="text">A request to refresh your password has reached us. Unveil this code to proceed with grace:</div>
                                <div class="otp-box">%s</div>
                                <div class="text">This fleeting code dances away in 10 minutes. If this wasn’t your doing, whisper to us at once.</div>
                                <div class="divider"></div>
                                <div class="text">Your peace of mind is our cherished vow.</div>
                            </div>
                            <div class="footer">
                                <p>© 2025 Flavor Fleet - All Rights Reserved <a href="#">Contact Us</a></p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """, name, otp);
                break;
            default:
                throw new IllegalArgumentException("Unknown email type: " + type);
        }

        helper.setText(htmlContent, true);
        mailSender.send(message);
    }

    private void scheduleOtpCleanup(String email) {
        new Thread(() -> {
            try {
                Thread.sleep(OTP_EXPIRY_MINUTES * 60 * 1000);
                otpStore.remove(email);
                OTP_TIMESTAMP.remove(email);
                pendingRegistrations.remove(email);
                logger.info("OTP and pending registration cleaned up for email: {}", email);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("OTP cleanup thread interrupted for email: {}", email);
            }
        }).start();
    }

    @Transactional(readOnly = true)
    public List<CartItem> getCartItems(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        List<CartItem> items = cartItemRepository.findByUserAndOrderIsNull(user);
        logger.info("Fetched {} cart items for email: {}", items.size(), email);
        return items;
    }

    @Transactional
    public CartItem addToCart(String email, CartItem cartItem) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        cartItem.setUser(user);
        CartItem existingItem = cartItemRepository.findByUserAndItemIdAndOrderIsNull(user, cartItem.getItemId());
        if (existingItem != null) {
            existingItem.setQuantity(existingItem.getQuantity() + cartItem.getQuantity());
            CartItem savedItem = cartItemRepository.save(existingItem);
            logger.info("Updated existing cart item with ID: {} for email: {}", savedItem.getId(), email);
            return savedItem;
        }
        CartItem savedItem = cartItemRepository.save(cartItem);
        logger.info("Added cart item with ID: {} for email: {}", savedItem.getId(), email);
        return savedItem;
    }

    @Transactional
    public CartItem updateCartItem(String email, CartItem cartItem) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        CartItem existingItem = cartItemRepository.findById(cartItem.getId())
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        if (!existingItem.getUser().getEmail().equals(email)) {
            logger.warn("Cart item {} does not belong to user {}", cartItem.getId(), email);
            throw new SecurityException("Unauthorized cart item update");
        }

        existingItem.setQuantity(cartItem.getQuantity());
        existingItem.setPrice(cartItem.getPrice());
        CartItem updatedItem = cartItemRepository.save(existingItem);
        logger.info("Updated cart item with ID: {} for email: {}", updatedItem.getId(), email);
        return updatedItem;
    }

    @Transactional
    public boolean removeFromCart(String email, Long id) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        cartItemRepository.deleteByUserAndId(user, id);
        boolean exists = cartItemRepository.existsById(id);
        logger.info("Removed cart item with ID: {} for email: {}. Item exists after deletion: {}", id, email, exists);
        return !exists;
    }

    @Transactional
    public void clearCart(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        List<CartItem> cartItems = cartItemRepository.findByUserAndOrderIsNull(user);
        cartItemRepository.deleteAll(cartItems);
        logger.info("Cleared cart for email: {}", email);
    }

    @Transactional(readOnly = true)
    public List<FavoriteItem> getFavoriteItems(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        List<FavoriteItem> items = favoriteItemRepository.findByUser(user);
        logger.info("Fetched {} favorite items for email: {}", items.size(), email);
        return items;
    }

    @Transactional
    public FavoriteItem addToFavorites(String email, FavoriteItem favoriteItem) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        if (favoriteItemRepository.existsByUserAndItemId(user, favoriteItem.getItemId())) {
            logger.info("Item {} already in favorites for email: {}", favoriteItem.getItemId(), email);
            return favoriteItemRepository.findByUser(user)
                    .stream()
                    .filter(item -> item.getItemId().equals(favoriteItem.getItemId()))
                    .findFirst()
                    .orElseThrow();
        }

        favoriteItem.setUser(user);
        if (favoriteItem.getPrice() == null) {
            favoriteItem.setPrice(0.0);
        }
        FavoriteItem savedItem = favoriteItemRepository.save(favoriteItem);
        logger.info("Added favorite item with ID: {} for email: {}", savedItem.getId(), email);
        return savedItem;
    }

    @Transactional
    public void removeFromFavorites(String email, Long itemId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        FavoriteItem favoriteItem = favoriteItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Favorite item not found"));

        if (!favoriteItem.getUser().getEmail().equals(email)) {
            logger.warn("Favorite item {} does not belong to user {}", itemId, email);
            throw new SecurityException("Unauthorized favorite item removal");
        }

        favoriteItemRepository.delete(favoriteItem);
        logger.info("Removed favorite item with ID: {} for email: {}", itemId, email);
    }
}