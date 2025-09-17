package com.flavorfleet.controller;

import com.flavorfleet.config.JwtUtil;
import com.flavorfleet.dto.*;
import com.flavorfleet.entity.Address;
import com.flavorfleet.entity.User;
import com.flavorfleet.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:8484")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
    private static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil, UserService userService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        logger.info("Login attempt for email: {}", request.getEmail());
        try {
            if (!Pattern.matches(EMAIL_REGEX, request.getEmail())) {
                logger.warn("Invalid email format: {}", request.getEmail());
                return ResponseEntity.badRequest()
                        .body(new AdminController.ErrorResponse("Invalid email format"));
            }

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            User user = userService.findByEmail(request.getEmail());
            if (user == null) {
                logger.warn("User not found in database for email: {}", request.getEmail());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AdminController.ErrorResponse("User not found"));
            }
            String token = jwtUtil.generateToken(user.getEmail());

            LoginResponse response = new LoginResponse(token, user.getEmail(), user.getName(), "Login successful");
            logger.info("Login successful for email: {}", request.getEmail());
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            logger.warn("Invalid credentials for email: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AdminController.ErrorResponse("Invalid email or password"));
        } catch (Exception e) {
            logger.error("Login failed for email: {}", request.getEmail(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AdminController.ErrorResponse("Login failed: " + e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        logger.info("Register attempt for email: {}", request.getEmail());
        try {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                logger.warn("Name is required for registration: {}", request.getEmail());
                return ResponseEntity.badRequest()
                        .body(new AdminController.ErrorResponse("Name is required"));
            }
            if (!Pattern.matches(EMAIL_REGEX, request.getEmail())) {
                logger.warn("Invalid email format: {}", request.getEmail());
                return ResponseEntity.badRequest()
                        .body(new AdminController.ErrorResponse("Invalid email format"));
            }

            if (!Pattern.matches(PASSWORD_REGEX, request.getPassword())) {
                logger.warn("Password does not meet complexity requirements for email: {}", request.getEmail());
                return ResponseEntity.badRequest()
                        .body(new AdminController.ErrorResponse(
                                "Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one digit, and one special character (@$!%*?&)"
                        ));
            }

            User user = new User();
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setPassword(request.getPassword());

            boolean otpSent = userService.sendOtpForSignup(request.getEmail(), user);
            if (!otpSent) {
                logger.warn("Email already exists: {}", request.getEmail());
                return ResponseEntity.badRequest()
                        .body(new AdminController.ErrorResponse("Email already exists"));
            }

            LoginResponse response = new LoginResponse(null, request.getEmail(), null, "OTP sent to your email for verification");
            logger.info("OTP sent for signup to email: {}", request.getEmail());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Registration failed for email: {}", request.getEmail(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AdminController.ErrorResponse("Registration failed: " + e.getMessage()));
        }
    }

    @PostMapping("/verify-signup-otp")
    public ResponseEntity<?> verifySignupOtp(@Valid @RequestBody VerifyOtpRequest request) {
        logger.info("OTP verification attempt for email: {}", request.getEmail());
        try {
            User verifiedUser = userService.verifySignupOtp(request.getEmail(), request.getOtp());
            if (verifiedUser == null) {
                logger.warn("Invalid OTP or email: {}", request.getEmail());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new AdminController.ErrorResponse("Invalid OTP or email"));
            }

            String token = jwtUtil.generateToken(verifiedUser.getEmail());
            LoginResponse response = new LoginResponse(token, verifiedUser.getEmail(), verifiedUser.getName(), "Registration successful");
            logger.info("Registration successful for email: {}", request.getEmail());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("OTP verification failed for email: {}", request.getEmail(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AdminController.ErrorResponse("OTP verification failed: " + e.getMessage()));
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(HttpServletRequest request) {
        String email = null;
        try {
            String token = extractToken(request);
            email = jwtUtil.getEmailFromToken(token);
            if (email == null) {
                logger.warn("Invalid token: unable to extract email");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new AdminController.ErrorResponse("Invalid token: Email not found in token"));
            }

            UserDetails userDetails = userService.loadUserByUsername(email);
            if (!jwtUtil.validateToken(token, userDetails)) {
                logger.warn("Token validation failed for email: {}", email);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AdminController.ErrorResponse("Token validation failed: Token may be expired or invalid"));
            }

            User user = userService.findByEmail(email);
            if (user == null) {
                logger.warn("User not found in database for email: {}", email);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AdminController.ErrorResponse("User not found"));
            }

            logger.info("Profile fetched successfully for email: {}. Role: {}", email, user.getRole());
            UserProfileDTO profile = new UserProfileDTO(
                    user.getId(),
                    user.getName() != null ? user.getName() : "",
                    user.getEmail(),
                    user.getRole() != null ? user.getRole().replace("ROLE_", "") : "USER",
                    user.getProfilePicture(),
                    user.getOrdersCount(),
                    user.getCartItemsCount(),
                    user.getFavoriteItemsCount(),
                    user.getAddresses() != null ? user.getAddresses().stream()
                            .map(addr -> new AddressDTO(addr.getId(), addr.getLine1(), addr.getLine2(), addr.getCity(), addr.getPincode()))
                            .collect(Collectors.toList()) : List.of(),
                    user.isEmailOrderUpdates(),
                    user.isEmailPromotions(),
                    user.isDesktopNotifications()
            );
            return ResponseEntity.ok(profile);
        } catch (IllegalArgumentException e) {
            logger.warn("Authentication error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AdminController.ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Profile fetch failed for email: {}", email != null ? email : "unknown", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new AdminController.ErrorResponse("Profile fetch failed: " + e.getMessage()));
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateUserProfile(HttpServletRequest request, @Valid @RequestBody UpdateProfileRequest updateRequest) {
        String email = null;
        try {
            String token = extractToken(request);
            email = jwtUtil.getEmailFromToken(token);
            if (email == null) {
                logger.warn("Invalid token: unable to extract email");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AdminController.ErrorResponse("Invalid token: Email not found in token"));
            }

            UserDetails userDetails = userService.loadUserByUsername(email);
            if (!jwtUtil.validateToken(token, userDetails)) {
                logger.warn("Token validation failed for email: {}", email);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AdminController.ErrorResponse("Token validation failed: Token may be expired or invalid"));
            }

            User user = userService.findByEmail(email);
            if (user == null) {
                logger.warn("User not found in database for email: {}", email);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AdminController.ErrorResponse("User not found"));
            }

            // Update only provided fields
            if (updateRequest.getName() != null && !updateRequest.getName().trim().isEmpty()) {
                user.setName(updateRequest.getName());
            }
            if (updateRequest.getEmail() != null && !updateRequest.getEmail().trim().isEmpty()) {
                if (!Pattern.matches(EMAIL_REGEX, updateRequest.getEmail())) {
                    logger.warn("Invalid new email format: {}", updateRequest.getEmail());
                    return ResponseEntity.badRequest()
                            .body(new AdminController.ErrorResponse("Invalid email format"));
                }
                if (!email.equals(updateRequest.getEmail())) {
                    User existingUserWithEmail = userService.findByEmail(updateRequest.getEmail());
                    if (existingUserWithEmail != null) {
                        logger.warn("Email already exists: {} (attempted by user: {})", updateRequest.getEmail(), email);
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(new AdminController.ErrorResponse("Email already exists"));
                    }
                }
                user.setEmail(updateRequest.getEmail());
            }
            if (updateRequest.getProfilePicture() != null) {
                user.setProfilePicture(updateRequest.getProfilePicture());
            }

            userService.save(user);

            logger.info("Profile updated successfully for email: {}", email);
            return ResponseEntity.ok(new AdminController.SuccessResponse("Profile updated successfully"));
        } catch (IllegalArgumentException e) {
            logger.warn("Authentication error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AdminController.ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Profile update failed for email: {}", email != null ? email : "unknown", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new AdminController.ErrorResponse("Profile update failed: " + e.getMessage()));
        }
    }

    @PostMapping("/addresses")
    public ResponseEntity<?> addAddress(HttpServletRequest request, @Valid @RequestBody AddressDTO addressDTO) {
        String email = null;
        try {
            String token = extractToken(request);
            email = jwtUtil.getEmailFromToken(token);
            if (email == null) {
                logger.warn("Invalid token: unable to extract email");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new AdminController.ErrorResponse("Invalid token: Email not found in token"));
            }

            UserDetails userDetails = userService.loadUserByUsername(email);
            if (!jwtUtil.validateToken(token, userDetails)) {
                logger.warn("Token validation failed for email: {}", email);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AdminController.ErrorResponse("Token validation failed: Token may be expired or invalid"));
            }

            User user = userService.findByEmail(email);
            if (user == null) {
                logger.warn("User not found in database for email: {}", email);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AdminController.ErrorResponse("User not found"));
            }

            Address address = new Address(
                    addressDTO.getLine1(),
                    addressDTO.getLine2(),
                    addressDTO.getCity(),
                    addressDTO.getPincode(),
                    user
            );
            user.addAddress(address);
            userService.save(user);

            logger.info("Address added successfully for email: {}", email);
            AddressDTO savedAddress = new AddressDTO(
                    address.getId(),
                    address.getLine1(),
                    address.getLine2(),
                    address.getCity(),
                    address.getPincode()
            );
            return ResponseEntity.ok(savedAddress);
        } catch (IllegalArgumentException e) {
            logger.warn("Authentication error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AdminController.ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Address addition failed for email: {}", email != null ? email : "unknown", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AdminController.ErrorResponse("Address addition failed: " + e.getMessage()));
        }
    }

    @PutMapping("/addresses/{addressId}")
    public ResponseEntity<?> updateAddress(HttpServletRequest request, @PathVariable Long addressId, @Valid @RequestBody AddressDTO addressDTO) {
        String email = null;
        try {
            String token = extractToken(request);
            email = jwtUtil.getEmailFromToken(token);
            if (email == null) {
                logger.warn("Invalid token: unable to extract email");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AdminController.ErrorResponse("Invalid token: Email not found in token"));
            }

            UserDetails userDetails = userService.loadUserByUsername(email);
            if (!jwtUtil.validateToken(token, userDetails)) {
                logger.warn("Token validation failed for email: {}", email);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AdminController.ErrorResponse("Token validation failed: Token may be expired or invalid"));
            }

            User user = userService.findByEmail(email);
            if (user == null) {
                logger.warn("User not found in database for email: {}", email);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AdminController.ErrorResponse("User not found"));
            }

            Address address = user.getAddresses().stream()
                    .filter(addr -> addr.getId().equals(addressId))
                    .findFirst()
                    .orElse(null);
            if (address == null) {
                logger.warn("Address not found for id: {} and email: {}", addressId, email);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AdminController.ErrorResponse("Address not found"));
            }

            address.setLine1(addressDTO.getLine1());
            address.setLine2(addressDTO.getLine2());
            address.setCity(addressDTO.getCity());
            address.setPincode(addressDTO.getPincode());
            userService.save(user);

            logger.info("Address updated successfully for id: {} and email: {}", addressId, email);
            AddressDTO updatedAddress = new AddressDTO(
                    address.getId(),
                    address.getLine1(),
                    address.getLine2(),
                    address.getCity(),
                    address.getPincode()
            );
            return ResponseEntity.ok(updatedAddress);
        } catch (IllegalArgumentException e) {
            logger.warn("Authentication error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AdminController.ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Address update failed for id: {} and email: {}", addressId, email != null ? email : "unknown", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AdminController.ErrorResponse("Address update failed: " + e.getMessage()));
        }
    }

    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<?> deleteAddress(HttpServletRequest request, @PathVariable Long addressId) {
        String email = null;
        try {
            String token = extractToken(request);
            email = jwtUtil.getEmailFromToken(token);
            if (email == null) {
                logger.warn("Invalid token: unable to extract email");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AdminController.ErrorResponse("Invalid token: Email not found in token"));
            }

            UserDetails userDetails = userService.loadUserByUsername(email);
            if (!jwtUtil.validateToken(token, userDetails)) {
                logger.warn("Token validation failed for email: {}", email);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AdminController.ErrorResponse("Token validation failed: Token may be expired or invalid"));
            }

            User user = userService.findByEmail(email);
            if (user == null) {
                logger.warn("User not found in database for email: {}", email);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AdminController.ErrorResponse("User not found"));
            }

            Address address = user.getAddresses().stream()
                    .filter(addr -> addr.getId().equals(addressId))
                    .findFirst()
                    .orElse(null);
            if (address == null) {
                logger.warn("Address not found for id: {} and email: {}", addressId, email);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AdminController.ErrorResponse("Address not found"));
            }

            user.removeAddress(address);
            userService.deleteAddress(address);
            userService.save(user);

            logger.info("Address deleted successfully for id: {} and email: {}", addressId, email);
            return ResponseEntity.ok(new AdminController.SuccessResponse("Address deleted successfully"));
        } catch (IllegalArgumentException e) {
            logger.warn("Authentication error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AdminController.ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Address deletion failed for id: {} and email: {}", addressId, email != null ? email : "unknown", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AdminController.ErrorResponse("Address deletion failed: " + e.getMessage()));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(HttpServletRequest request, @Valid @RequestBody ChangePasswordRequest changeRequest) {
        String email = null;
        try {
            String token = extractToken(request);
            email = jwtUtil.getEmailFromToken(token);
            logger.info("Change password request received for email: {}", email);

            if (email == null) {
                logger.warn("Invalid token: unable to extract email");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AdminController.ErrorResponse("Invalid token: Email not found in token"));
            }

            UserDetails userDetails = userService.loadUserByUsername(email);
            if (!jwtUtil.validateToken(token, userDetails)) {
                logger.warn("Token validation failed for email: {}", email);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AdminController.ErrorResponse("Token validation failed: Token may be expired or invalid"));
            }

            if (!Pattern.matches(PASSWORD_REGEX, changeRequest.getNewPassword())) {
                logger.warn("New password does not meet complexity requirements for email: {}", email);
                return ResponseEntity.badRequest()
                        .body(new AdminController.ErrorResponse(
                                "New password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one digit, and one special character (@$!%*?&)"
                        ));
            }

            boolean success = userService.changePassword(email, changeRequest.getCurrentPassword(), changeRequest.getNewPassword());
            if (success) {
                logger.info("Password changed successfully for email: {}", email);
                return ResponseEntity.ok(new AdminController.SuccessResponse("Password changed successfully"));
            } else {
                logger.warn("Current password incorrect for email: {}", email);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AdminController.ErrorResponse("Current password is incorrect"));
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Authentication error for email {}: {}", email != null ? email : "unknown", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AdminController.ErrorResponse("Authentication failed: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Password change failed for email: {}", email != null ? email : "unknown", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AdminController.ErrorResponse("Password change failed: " + e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        logger.info("Forgot password request for email: {}", request.getEmail());
        try {
            if (!Pattern.matches(EMAIL_REGEX, request.getEmail())) {
                logger.warn("Invalid email format: {}", request.getEmail());
                return ResponseEntity.badRequest()
                    .body(new AdminController.ErrorResponse("Invalid email format"));
            }

            boolean success = userService.sendOtpForPasswordReset(request.getEmail());
            if (success) {
                logger.info("OTP sent successfully to email: {}", request.getEmail());
                return ResponseEntity.ok(new AdminController.SuccessResponse("OTP sent to your email"));
            } else {
                logger.warn("Email not found: {}", request.getEmail());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new AdminController.ErrorResponse("Email not found"));
            }
        } catch (Exception e) {
            logger.error("Failed to send OTP for email: {}", request.getEmail(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AdminController.ErrorResponse("Failed to send OTP: " + e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        logger.info("Reset password attempt for email: {}", request.getEmail());
        try {
            if (!Pattern.matches(EMAIL_REGEX, request.getEmail())) {
                logger.warn("Invalid email format: {}", request.getEmail());
                return ResponseEntity.badRequest()
                    .body(new AdminController.ErrorResponse("Invalid email format"));
            }

            if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                logger.warn("Passwords do not match for email: {}", request.getEmail());
                return ResponseEntity.badRequest()
                    .body(new AdminController.ErrorResponse("Passwords do not match"));
            }

            if (!Pattern.matches(PASSWORD_REGEX, request.getNewPassword())) {
                logger.warn("New password does not meet complexity requirements for email: {}", request.getEmail());
                return ResponseEntity.badRequest()
                    .body(new AdminController.ErrorResponse(
                            "New password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one digit, and one special character (@$!%*?&)"
                    ));
            }

            boolean success = userService.resetPassword(request.getEmail(), request.getOtp(), request.getNewPassword());
            if (success) {
                logger.info("Password reset successful for email: {}", request.getEmail());
                return ResponseEntity.ok(new AdminController.SuccessResponse("Password reset successful"));
            } else {
                logger.warn("Invalid OTP or email: {}", request.getEmail());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AdminController.ErrorResponse("Invalid OTP or email"));
            }
        } catch (Exception e) {
            logger.error("Reset password failed for email: {}", request.getEmail(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new AdminController.ErrorResponse("Password reset failed: " + e.getMessage()));
        }
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Invalid or missing Authorization header");
        }
        return authHeader.substring(7);
    }

    public static class UserProfileDTO {
        private Long id;
        private String name;
        private String email;
        private String role;
        private String profilePicture;
        private int ordersCount;
        private int cartItemsCount;
        private int favoriteItemsCount;
        private List<AddressDTO> addresses;
        private Boolean emailOrderUpdates; // Changed to Boolean
        private Boolean emailPromotions; // Changed to Boolean
        private Boolean desktopNotifications; // Changed to Boolean

        public UserProfileDTO(Long id, String name, String email, String role, String profilePicture, int ordersCount,
                             int cartItemsCount, int favoriteItemsCount, List<AddressDTO> addresses,
                             Boolean emailOrderUpdates, Boolean emailPromotions, Boolean desktopNotifications) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.role = role;
            this.profilePicture = profilePicture;
            this.ordersCount = ordersCount;
            this.cartItemsCount = cartItemsCount;
            this.favoriteItemsCount = favoriteItemsCount;
            this.addresses = addresses;
            this.emailOrderUpdates = emailOrderUpdates;
            this.emailPromotions = emailPromotions;
            this.desktopNotifications = desktopNotifications;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getProfilePicture() { return profilePicture; }
        public void setProfilePicture(String profilePicture) { this.profilePicture = profilePicture; }
        public int getOrdersCount() { return ordersCount; }
        public void setOrdersCount(int ordersCount) { this.ordersCount = ordersCount; }
        public int getCartItemsCount() { return cartItemsCount; }
        public void setCartItemsCount(int cartItemsCount) { this.cartItemsCount = cartItemsCount; }
        public int getFavoriteItemsCount() { return favoriteItemsCount; }
        public void setFavoriteItemsCount(int favoriteItemsCount) { this.favoriteItemsCount = favoriteItemsCount; }
        public List<AddressDTO> getAddresses() { return addresses; }
        public void setAddresses(List<AddressDTO> addresses) { this.addresses = addresses; }
        public Boolean isEmailOrderUpdates() { return emailOrderUpdates; }
        public void setEmailOrderUpdates(Boolean emailOrderUpdates) { this.emailOrderUpdates = emailOrderUpdates; }
        public Boolean isEmailPromotions() { return emailPromotions; }
        public void setEmailPromotions(Boolean emailPromotions) { this.emailPromotions = emailPromotions; }
        public Boolean isDesktopNotifications() { return desktopNotifications; }
        public void setDesktopNotifications(Boolean desktopNotifications) { this.desktopNotifications = desktopNotifications; }
    }
}
