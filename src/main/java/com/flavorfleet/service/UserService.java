package com.flavorfleet.service;

import com.flavorfleet.dto.AdminStatsDTO;
import com.flavorfleet.dto.AdminUserDTO;
import com.flavorfleet.entity.Address;
import com.flavorfleet.entity.CartItem;
import com.flavorfleet.entity.FavoriteItem;
import com.flavorfleet.entity.Order;
import com.flavorfleet.entity.RefreshToken;
import com.flavorfleet.entity.User;
import com.flavorfleet.repository.AddressRepository;
import com.flavorfleet.repository.CartItemRepository;
import com.flavorfleet.repository.FavoriteItemRepository;
import com.flavorfleet.repository.NotificationRepository;
import com.flavorfleet.repository.OrderRepository;
import com.flavorfleet.repository.RefreshTokenRepository;
import com.flavorfleet.repository.UserRepository;
import jakarta.annotation.PostConstruct;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final NotificationService notificationService;
    private final OrderService orderService;
    private final MenuService menuService; // NEW: Added MenuService

    @Value("${spring.mail.from}")
    private String fromEmail;

    public UserService(UserRepository userRepository,
                       AddressRepository addressRepository,
                       CartItemRepository cartItemRepository,
                       FavoriteItemRepository favoriteItemRepository,
                       OrderRepository orderRepository,
                       NotificationRepository notificationRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JavaMailSender mailSender,
                       @Lazy NotificationService notificationService,
                       @Lazy OrderService orderService,
                       MenuService menuService) { // NEW: Added MenuService to constructor
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.cartItemRepository = cartItemRepository;
        this.favoriteItemRepository = favoriteItemRepository;
        this.orderRepository = orderRepository;
        this.notificationRepository = notificationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.notificationService = notificationService;
        this.orderService = orderService;
        this.menuService = menuService; // NEW: Initialize MenuService
    }

    // Updated: Auto-activate ALL admin accounts on startup (permanent for admins)
    @PostConstruct
    public void ensureAdminAccountsAreActive() {
        List<String> adminEmails = List.of(
                "saketh.surubhotla@gmail.com",
                "suthapallichakradhar@gmail.com"
                // Add any other admin emails here
        );
        for (String email : adminEmails) {
            userRepository.findByEmail(email).ifPresent(user -> {
                if (!user.isActive()) {
                    user.setActive(true);
                    userRepository.save(user);
                    logger.info("Fixed active status for admin account: {}", email);
                }
            });
        }
    }

    // FIXED: Handle multiple users with same email
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        logger.debug("Loading user by email: {}", email);
       
        // Use findAllByEmail to handle potential duplicates
        List<User> users = userRepository.findAllByEmail(email);
       
        if (users.isEmpty()) {
            throw new UsernameNotFoundException("User not found with email: " + email);
        }
       
        // Handle multiple users with same email (should not happen, but just in case)
        if (users.size() > 1) {
            logger.error("MULTIPLE USERS FOUND with email: {}. Count: {}. This should be fixed in database!",
                email, users.size());
           
            // Log all duplicate users for debugging
            for (User u : users) {
                logger.error("Duplicate user - ID: {}, Name: {}, Created: {}, Role: {}",
                    u.getId(), u.getName(), u.getCreatedAt(), u.getRole());
            }
           
            // Use the most recent user as a temporary fix
            User mostRecent = users.stream()
                .max(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(users.get(0));
               
            logger.warn("TEMPORARY FIX: Using most recent user for email: {} - ID: {}, Created: {}",
                email, mostRecent.getId(), mostRecent.getCreatedAt());
           
            User user = mostRecent;
            String role = user.getRole() != null ? user.getRole() : "ROLE_USER";
           
            return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                true, true, true, true,
                Collections.singletonList(new SimpleGrantedAuthority(role))
            );
        }
       
        User user = users.get(0);
        logger.debug("User found: {}. Role: {}", user.getEmail(), user.getRole());
       
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

    // Get filtered users for admin table (by role and status)
    @Transactional(readOnly = true)
    public List<AdminUserDTO> getUsersWithStatus(String roleFilter, String statusFilter) {
        logger.info("Fetching users with filters - role: {}, status: {}", roleFilter, statusFilter);
        List<User> users;
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
       
        if ("ALL".equals(roleFilter) && "ALL".equals(statusFilter)) {
            users = userRepository.findAll();
        } else if ("ALL".equals(roleFilter)) {
            // Filter only by status
            if ("ACTIVE".equals(statusFilter)) {
                users = userRepository.findActiveUsers(thirtyDaysAgo);
            } else if ("INACTIVE".equals(statusFilter)) {
                users = userRepository.findInactiveUsers(thirtyDaysAgo);
            } else {
                users = userRepository.findAll();
            }
        } else if ("ALL".equals(statusFilter)) {
            // Filter only by role
            users = userRepository.findByRole("ROLE_" + roleFilter.toUpperCase());
        } else {
            // Combined filter
            if ("ACTIVE".equals(statusFilter)) {
                users = userRepository.findActiveUsersByRole("ROLE_" + roleFilter.toUpperCase(), thirtyDaysAgo);
            } else {
                // For inactive, use the new repository method
                users = userRepository.findInactiveUsersByRole("ROLE_" + roleFilter.toUpperCase(), thirtyDaysAgo);
            }
        }
       
        // Convert to DTOs
        return users.stream()
                .map(user -> new AdminUserDTO(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        null, // Phone placeholder
                        user.getRole(),
                        user.getLastLogin()
                ))
                .collect(Collectors.toList());
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
            // Load user for deleteByUser
            User user = userRepository.findById(id).get();
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
            refreshTokenRepository.deleteByUser(user);
            logger.debug("Deleted refresh tokens for user ID: {}", id);
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
        logger.debug("Encoding password during signup for email: {}.", email);
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
            sendEmail(email, name, null, "Welcome to Flavor Fleet - Let's Embark Together", "welcome");
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
                        <style>
                            body { margin: 0; padding: 0; background: #f9f7f2; font-family: 'Helvetica', sans-serif; }
                            .container { max-width: 720px; margin: 50px auto; background: #fff; border-radius: 20px; overflow: hidden; box-shadow: 0 20px 80px rgba(0,0,0,0.15); }
                            .header { background: linear-gradient(120deg, #2c3e50, #e74c3c, #f1c40f); padding: 50px 30px; text-align: center; color: #fff; }
                            .header h1 { font-size: 38px; margin: 0; }
                            .content { padding: 60px 40px; background: #fff; }
                            .otp-box { background: linear-gradient(135deg, #f1c40f, #e67e22); padding: 20px; text-align: center; font-size: 2.8rem; letter-spacing: 12px; border-radius: 15px; color: #1c2526; font-weight: 900; margin: 30px 0; }
                            .greeting { font-size: 26px; color: #2c3e50; margin-bottom: 20px; }
                            .footer { background: #2c3e50; padding: 30px; text-align: center; color: #fff; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header"><h1>Flavor Fleet</h1></div>
                            <div class="content">
                                <div class="greeting">Greetings %s,</div>
                                <div class="otp-box">%s</div>
                                <div>This code expires in 10 minutes.</div>
                            </div>
                            <div class="footer"><p>© 2025 Flavor Fleet</p></div>
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
                        <style>
                            body { margin: 0; padding: 0; background: #f9f7f2; font-family: 'Helvetica', sans-serif; }
                            .container { max-width: 720px; margin: 50px auto; background: #fff; border-radius: 20px; overflow: hidden; box-shadow: 0 20px 80px rgba(0,0,0,0.15); }
                            .header { background: linear-gradient(120deg, #2c3e50, #e74c3c, #f1c40f); padding: 60px 30px; text-align: center; color: #fff; }
                            .header h1 { font-size: 42px; margin: 0; }
                            .content { padding: 60px 40px; background: #fff; }
                            .welcome-box { background: linear-gradient(135deg, #f1c40f, #e67e22); padding: 25px; border-radius: 15px; text-align: center; color: #1c2526; font-size: 2rem; font-weight: 700; margin: 30px 0; }
                            .greeting { font-size: 28px; color: #2c3e50; margin-bottom: 25px; }
                            .footer { background: #2c3e50; padding: 30px; text-align: center; color: #fff; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header"><h1>Flavor Fleet</h1></div>
                            <div class="content">
                                <div class="greeting">Warmest Welcome, %s!</div>
                                <div class="welcome-box">Account Verified</div>
                                <div>Your adventure with Flavor Fleet begins today!</div>
                            </div>
                            <div class="footer"><p>© 2025 Flavor Fleet</p></div>
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
                        <style>
                            body { margin: 0; padding: 0; background: #f9f7f2; font-family: 'Helvetica', sans-serif; }
                            .container { max-width: 720px; margin: 50px auto; background: #fff; border-radius: 20px; overflow: hidden; box-shadow: 0 20px 80px rgba(0,0,0,0.15); }
                            .header { background: linear-gradient(120deg, #2c3e50, #e74c3c, #f1c40f); padding: 50px 30px; text-align: center; color: #fff; }
                            .header h1 { font-size: 38px; margin: 0; }
                            .content { padding: 60px 40px; background: #fff; }
                            .otp-box { background: linear-gradient(135deg, #f1c40f, #e67e22); padding: 20px; text-align: center; font-size: 2.8rem; letter-spacing: 12px; border-radius: 15px; color: #1c2526; font-weight: 900; margin: 30px 0; }
                            .greeting { font-size: 26px; color: #2c3e50; margin-bottom: 20px; }
                            .footer { background: #2c3e50; padding: 30px; text-align: center; color: #fff; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header"><h1>Flavor Fleet</h1></div>
                            <div class="content">
                                <div class="greeting">Dear %s,</div>
                                <div class="otp-box">%s</div>
                                <div>This code expires in 10 minutes.</div>
                            </div>
                            <div class="footer"><p>© 2025 Flavor Fleet</p></div>
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

    // FIXED: Get comprehensive admin stats for dashboard with menu stats
    @Transactional(readOnly = true)
    public AdminStatsDTO getAdminStats(String timeRange) {
        logger.info("Computing admin stats for time range: {}", timeRange);
    
        // User stats (always all-time for total/active)
        long totalUsers = userRepository.countAllUsers();
        long adminUsers = userRepository.countByRole("ROLE_ADMIN");
        long userCount = userRepository.countByRole("ROLE_USER");
    
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        long activeUsers = userRepository.countActiveUsers(thirtyDaysAgo);
        long inactiveUsers = totalUsers - activeUsers;
       
        // New users for time range
        long newUsers = 0;
        LocalDateTime timeStart = null;
        if ("7d".equals(timeRange)) {
            timeStart = LocalDateTime.now().minusDays(7);
            newUsers = userRepository.countByCreatedAtAfter(timeStart);
        } else if ("30d".equals(timeRange)) {
            timeStart = LocalDateTime.now().minusDays(30);
            newUsers = userRepository.countByCreatedAtAfter(timeStart);
        } else { // "all"
            newUsers = totalUsers;
        }
       
        // Order stats (use OrderService for filtered orders)
        List<Order> filteredOrders = orderService.getFilteredOrdersForStats(timeRange);
        long totalOrders = filteredOrders.size();
        BigDecimal totalRevenue = filteredOrders.stream()
                .map(Order::getTotalPrice)
                .filter(Objects::nonNull)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgOrderValue = totalOrders > 0 ?
                totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
       
        // Order status count
        Map<String, Long> orderStatusCount = filteredOrders.stream()
                .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));
       
        // FIXED: Get menu stats from MenuService
        long totalMenuItems = menuService.getAllMenuItems().size();
        long totalCategories = menuService.getAllCategories().size();
       
        // Mock trend (replace with real calculation later)
        double revenueTrendPercent = 12.5; // Positive 12.5%
       
        AdminStatsDTO stats = new AdminStatsDTO(
                totalUsers, activeUsers, inactiveUsers, adminUsers, newUsers,
                totalOrders, totalRevenue, avgOrderValue,
                orderStatusCount, totalMenuItems, totalCategories,
                timeRange, timeStart, LocalDateTime.now(), revenueTrendPercent
        );
       
        logger.info("Admin stats computed: totalUsers={}, activeUsers={}, totalOrders={}, totalRevenue={}, menuItems={}",
                totalUsers, activeUsers, totalOrders, totalRevenue, totalMenuItems);
        return stats;
    }

    // Method to store refresh token
    @Transactional
    public void storeRefreshToken(String refreshToken, User user) {
        // Delete old refresh tokens for this user (one per user policy)
        refreshTokenRepository.deleteByUser(user);
    
        LocalDateTime expiry = LocalDateTime.now().plusDays(7); // Match REFRESH_TOKEN_VALIDITY
        RefreshToken rt = new RefreshToken(refreshToken, user, expiry);
        refreshTokenRepository.save(rt);
        logger.info("Stored new refresh token for user: {}", user.getEmail());
    }

    // Method to update/rotate refresh token
    @Transactional
    public void updateRefreshToken(String newRefreshToken, String email) {
        User user = findByEmail(email);
        if (user != null) {
            storeRefreshToken(newRefreshToken, user);
        }
    }

    // Validate refresh token
    public boolean isValidRefreshToken(String token, String email) {
        Optional<RefreshToken> rtOpt = refreshTokenRepository.findByToken(token);
        if (rtOpt.isEmpty()) {
            return false;
        }
        RefreshToken rt = rtOpt.get();
        return rt.getUser().getEmail().equals(email) && rt.getExpiryDate().isAfter(LocalDateTime.now());
    }

    // Helper to generate random password for new restaurant owners
    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@$!%*?&";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // Create restaurant owner user (called from PartnerService.approveApplication)
    @Transactional
    public User createRestaurantOwner(String name, String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }
       
        String randomPassword = generateRandomPassword();
        String encodedPassword = passwordEncoder.encode(randomPassword);
       
        User user = new User(name, email, encodedPassword, "ROLE_RESTAURANT_OWNER");
        user.setPasswordChanged(false); // NEW: Force password change on first login
        User saved = save(user);
       
        sendCredentialsEmail(email, name, randomPassword);
        logger.info("Created restaurant owner account for email: {}", email);
        return saved;
    }

    // Send credentials email
    private void sendCredentialsEmail(String email, String name, String password) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(email);
            helper.setSubject("Flavor Fleet - Your Restaurant Partner Account is Ready");
            helper.setFrom(fromEmail);
           
            String htmlContent = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { margin: 0; padding: 0; background: #f9f7f2; font-family: 'Helvetica', sans-serif; }
                        .container { max-width: 720px; margin: 50px auto; background: #fff; border-radius: 20px; overflow: hidden; box-shadow: 0 20px 80px rgba(0,0,0,0.15); }
                        .header { background: linear-gradient(120deg, #2c3e50, #e74c3c, #f1c40f); padding: 50px 30px; text-align: center; color: #fff; }
                        .header h1 { font-size: 38px; margin: 0; }
                        .content { padding: 60px 40px; background: #fff; }
                        .credentials-box { background: #f8f9fa; padding: 20px; border-radius: 8px; margin: 30px 0; border-left: 4px solid #e74c3c; }
                        .button { display: block; margin: 20px auto; padding: 12px 24px; background: #111827; color: white; text-decoration: none; border-radius: 8px; text-align: center; max-width: 200px; }
                        .footer { background: #2c3e50; padding: 30px; text-align: center; color: #fff; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Flavor Fleet Partners</h1>
                        </div>
                        <div class="content">
                            <h2>Congratulations, %s!</h2>
                            <p>Your restaurant partner application has been approved. You can now log in to your dedicated dashboard.</p>
                            <div class="credentials-box">
                                <p><strong>Email:</strong> %s</p>
                                <p><strong>Temporary Password:</strong> %s</p>
                                <p style="color: #e74c3c;">Please change your password immediately after logging in.</p>
                            </div>
                            <a href="http://localhost:8484/login" class="button">Log In Now</a>
                            <p>Access your dashboard at /owner/dashboard after login.</p>
                        </div>
                        <div class="footer">
                            <p>© 2025 Flavor Fleet - Partner Support</p>
                        </div>
                    </div>
                </body>
                </html>
                """, name, email, password);
           
            helper.setText(htmlContent, true);
            mailSender.send(message);
            logger.info("Credentials email sent successfully to: {}", email);
        } catch (MessagingException e) {
            logger.error("Failed to send credentials email to {}: {}", email, e.getMessage(), e);
        }
    }

    // Deactivate a user account (e.g., revoke restaurant access)
    @Transactional
    public boolean deactivateUser(Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            logger.warn("User not found for deactivation: {}", id);
            return false;
        }
       
        if ("ROLE_ADMIN".equals(user.getRole())) {
            throw new IllegalArgumentException("Cannot deactivate admin accounts");
        }
       
        user.setActive(false);
        userRepository.save(user);
       
        // Send deactivation email
        sendDeactivationEmail(user.getEmail(), user.getName());
       
        logger.info("Deactivated user ID: {} with role: {}", id, user.getRole());
        return true;
    }

    // Send deactivation email
    private void sendDeactivationEmail(String email, String name) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(email);
            helper.setSubject("Flavor Fleet - Account Deactivated");
            helper.setFrom(fromEmail);
           
            String htmlContent = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { margin: 0; padding: 0; background: #f9f7f2; font-family: 'Helvetica', sans-serif; }
                        .container { max-width: 720px; margin: 50px auto; background: #fff; border-radius: 20px; overflow: hidden; box-shadow: 0 20px 80px rgba(0,0,0,0.15); }
                        .header { background: linear-gradient(120deg, #2c3e50, #e74c3c, #f1c40f); padding: 50px 30px; text-align: center; color: #fff; }
                        .content { padding: 60px 40px; background: #fff; }
                        .footer { background: #2c3e50; padding: 30px; text-align: center; color: #fff; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Flavor Fleet</h1>
                        </div>
                        <div class="content">
                            <h2>Dear %s,</h2>
                            <p>Your account has been deactivated by an administrator.</p>
                            <p>If you believe this is an error, please contact support@flavorfleet.com</p>
                        </div>
                        <div class="footer">
                            <p>© 2025 Flavor Fleet</p>
                        </div>
                    </div>
                </body>
                </html>
                """, name);
           
            helper.setText(htmlContent, true);
            mailSender.send(message);
            logger.info("Deactivation email sent to: {}", email);
        } catch (MessagingException e) {
            logger.error("Failed to send deactivation email to {}: {}", email, e.getMessage());
        }
    }

    // NEW: Mark password as changed after successful update
    @Transactional
    public void markPasswordChanged(String email) {
        User user = findByEmail(email);
        if (user != null && !user.isPasswordChanged()) {
            user.setPasswordChanged(true);
            save(user);
            logger.info("Marked password as changed for email: {}", email);
        } else if (user != null) {
            logger.debug("Password already changed for email: {}", email);
        } else {
            logger.warn("User not found to mark password changed: {}", email);
        }
    }
}