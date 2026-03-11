package com.flavorfleet.controller;
import com.flavorfleet.config.JwtUtil;
import com.flavorfleet.dto.*;
import com.flavorfleet.entity.Address; // ← FIXED: Added missing import
import com.flavorfleet.entity.Order;
import com.flavorfleet.entity.User;
import com.flavorfleet.service.MenuService;
import com.flavorfleet.service.NotificationService;
import com.flavorfleet.service.OrderService;
import com.flavorfleet.service.PartnerService; // NEW: Added import for PartnerService
import com.flavorfleet.service.UserService;
import org.slf4j.Logger; // ← FIXED: Added missing import
import org.slf4j.LoggerFactory; // ← FIXED: Added missing import
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "http://localhost:8484")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminController {
    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);
    private final UserService userService;
    private final OrderService orderService;
    private final MenuService menuService;
    private final NotificationService notificationService;
    private final JwtUtil jwtUtil;
    private final PartnerService partnerService; // NEW: Field for PartnerService
    public AdminController(UserService userService,
                           OrderService orderService,
                           MenuService menuService,
                           NotificationService notificationService,
                           JwtUtil jwtUtil,
                           PartnerService partnerService) { // NEW: Added to constructor
        this.userService = userService;
        this.orderService = orderService;
        this.menuService = menuService;
        this.notificationService = notificationService;
        this.jwtUtil = jwtUtil;
        this.partnerService = partnerService;
    }
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(@RequestParam(defaultValue = "ALL") String role,
                                         @RequestParam(defaultValue = "ALL") String status,
                                         HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Fetching users by admin: {} with filters role={}, status={}", email, role, status);
        try {
            List<AdminUserDTO> users = userService.getUsersWithStatus(role, status);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            logger.error("Error fetching users: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch users: " + e.getMessage()));
        }
    }
    @DeleteMapping("/users/bulk")
    public ResponseEntity<?> deleteBulkUsers(@RequestBody List<Long> userIds, HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} attempting to bulk delete {} users", email, userIds.size());
        if (userIds == null || userIds.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("No user IDs provided"));
        }
        try {
            long deletedCount = 0;
            for (Long id : userIds) {
                if (userService.deleteUser(id)) {
                    deletedCount++;
                }
            }
            return ResponseEntity.ok(new SuccessResponse(deletedCount + " users deleted successfully"));
        } catch (Exception e) {
            logger.error("Bulk delete failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Bulk delete failed: " + e.getMessage()));
        }
    }
    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id,
                                            @RequestBody UpdateUserDTO dto,
                                            HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} updating user role ID: {} to {}", email, id, dto.getRole());
        try {
            boolean updated = userService.updateUser(id, dto.getRole());
            if (updated) {
                return ResponseEntity.ok(new SuccessResponse("User role updated successfully. User must re-login for changes to take effect."));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("User not found with ID: " + id));
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to update user role: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id, HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} attempting to delete user with ID: {}", email, id);
        User adminUser = userService.findByEmail(email);
        if (adminUser != null && adminUser.getId().equals(id)) {
            logger.warn("Admin {} attempted to delete their own account", email);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Cannot delete your own account"));
        }
        try {
            boolean deleted = userService.deleteUser(id);
            if (!deleted) {
                logger.warn("User not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("User not found with ID: " + id));
            }
            logger.info("User deleted successfully by admin {}: ID {}", email, id);
            return ResponseEntity.ok(new SuccessResponse("User deleted successfully"));
        } catch (Exception e) {
            logger.error("Failed to delete user with ID: {} by admin: {}", id, email, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to delete user: " + e.getMessage()));
        }
    }
    @GetMapping("/stats")
    public ResponseEntity<?> getAdminStats(@RequestParam(defaultValue = "30d") String timeRange,
                                           HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} fetching dashboard stats for timeRange: {}", email, timeRange);
        try {
            AdminStatsDTO stats = userService.getAdminStats(timeRange);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error fetching admin stats: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch dashboard stats: " + e.getMessage()));
        }
    }
    @GetMapping("/profile")
    public ResponseEntity<?> getAdminProfile(HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} fetching their profile", email);
        try {
            User user = userService.findByEmail(email);
            if (user == null) {
                logger.warn("Admin user not found: {}", email);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("User not found"));
            }
            AuthController.UserProfileDTO profile = new AuthController.UserProfileDTO(
                    user.getId(),
                    user.getName() != null ? user.getName() : "",
                    user.getEmail(),
                    user.getRole() != null ? user.getRole().replace("ROLE_", "") : "ADMIN",
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
        } catch (Exception e) {
            logger.error("Error fetching admin profile for {}: {}", email, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch admin profile: " + e.getMessage()));
        }
    }
    @PutMapping("/profile")
    public ResponseEntity<?> updateAdminProfile(HttpServletRequest request,
                                                @Valid @RequestBody UpdateProfileRequest updateRequest) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} updating their profile", email);
        try {
            User user = userService.findByEmail(email);
            if (user == null) {
                logger.warn("Admin user not found: {}", email);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("User not found"));
            }
            if (updateRequest.getName() != null && !updateRequest.getName().trim().isEmpty()) {
                user.setName(updateRequest.getName());
            }
            if (updateRequest.getEmail() != null && !updateRequest.getEmail().trim().isEmpty()) {
                if (!user.getEmail().equals(updateRequest.getEmail())) {
                    User existing = userService.findByEmail(updateRequest.getEmail());
                    if (existing != null) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(new ErrorResponse("Email already exists"));
                    }
                }
                user.setEmail(updateRequest.getEmail());
            }
            if (updateRequest.getProfilePicture() != null) {
                user.setProfilePicture(updateRequest.getProfilePicture());
            }
            userService.save(user);
            return ResponseEntity.ok(new SuccessResponse("Admin profile updated successfully"));
        } catch (Exception e) {
            logger.error("Error updating admin profile for {}: {}", email, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to update admin profile: " + e.getMessage()));
        }
    }
    @PostMapping("/addresses")
    public ResponseEntity<?> addAdminAddress(HttpServletRequest request,
                                             @Valid @RequestBody AddressDTO addressDTO) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} adding address", email);
        try {
            User user = userService.findByEmail(email);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("User not found"));
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
            AddressDTO savedAddress = new AddressDTO(
                    address.getId(),
                    address.getLine1(),
                    address.getLine2(),
                    address.getCity(),
                    address.getPincode()
            );
            return ResponseEntity.ok(savedAddress);
        } catch (Exception e) {
            logger.error("Error adding admin address for {}: {}", email, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to add admin address: " + e.getMessage()));
        }
    }
    @PutMapping("/addresses/{addressId}")
    public ResponseEntity<?> updateAdminAddress(HttpServletRequest request,
                                                @PathVariable Long addressId,
                                                @Valid @RequestBody AddressDTO addressDTO) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} updating address {}", email, addressId);
        try {
            User user = userService.findByEmail(email);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("User not found"));
            }
            Address address = user.getAddresses().stream()
                    .filter(a -> a.getId().equals(addressId))
                    .findFirst()
                    .orElse(null);
            if (address == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("Address not found"));
            }
            address.setLine1(addressDTO.getLine1());
            address.setLine2(addressDTO.getLine2());
            address.setCity(addressDTO.getCity());
            address.setPincode(addressDTO.getPincode());
            userService.save(user);
            AddressDTO updatedAddress = new AddressDTO(
                    address.getId(),
                    address.getLine1(),
                    address.getLine2(),
                    address.getCity(),
                    address.getPincode()
            );
            return ResponseEntity.ok(updatedAddress);
        } catch (Exception e) {
            logger.error("Error updating admin address for {}: {}", email, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to update admin address: " + e.getMessage()));
        }
    }
    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<?> deleteAdminAddress(HttpServletRequest request,
                                                @PathVariable Long addressId) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} deleting address {}", email, addressId);
        try {
            User user = userService.findByEmail(email);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("User not found"));
            }
            Address address = user.getAddresses().stream()
                    .filter(a -> a.getId().equals(addressId))
                    .findFirst()
                    .orElse(null);
            if (address == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("Address not found"));
            }
            user.removeAddress(address);
            userService.deleteAddress(address);
            userService.save(user);
            return ResponseEntity.ok(new SuccessResponse("Admin address deleted successfully"));
        } catch (Exception e) {
            logger.error("Error deleting admin address for {}: {}", email, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to delete admin address: " + e.getMessage()));
        }
    }
    @PostMapping("/change-password")
    public ResponseEntity<?> changeAdminPassword(HttpServletRequest request,
                                                 @Valid @RequestBody ChangePasswordRequest changeRequest) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} changing password", email);
        try {
            boolean success = userService.changePassword(email, changeRequest.getCurrentPassword(), changeRequest.getNewPassword());
            if (success) {
                return ResponseEntity.ok(new SuccessResponse("Admin password changed successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("Current password is incorrect"));
            }
        } catch (Exception e) {
            logger.error("Error changing admin password for {}: {}", email, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to change admin password: " + e.getMessage()));
        }
    }
    @GetMapping("/orders")
    public ResponseEntity<List<OrderDTO>> getAllOrders(HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} fetching all orders", email);
        try {
            List<Order> orders = orderService.getAllOrders();
            List<OrderDTO> orderDTOs = orders.stream()
                    .map(order -> new OrderDTO(
                            order.getId(),
                            order.getUser().getEmail(),
                            order.getUser().getName(),
                            order.getTotalPrice(),
                            order.getStatus(),
                            order.getCreatedAt(),
                            order.getItems().stream()
                                    .map(item -> new CartItemDTO(
                                            item.getId(),
                                            item.getItemId(),
                                            item.getName(),
                                            item.getPrice(),
                                            item.getQuantity(),
                                            item.getImage()))
                                    .collect(Collectors.toList()),
                            order.getAddressLine1(),
                            order.getAddressLine2(),
                            order.getCity(),
                            order.getPincode()))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(orderDTOs);
        } catch (Exception e) {
            logger.error("Error fetching orders: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }
    @PutMapping("/orders/{id}")
    public ResponseEntity<?> updateOrderStatus(@PathVariable Long id,
                                               @RequestBody Map<String, String> statusMap,
                                               HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        String newStatus = statusMap.get("status");
        logger.info("Admin {} updating order {} status to {}", email, id, newStatus);
        try {
            boolean updated = userService.updateOrderStatus(id, newStatus);
            if (updated) {
                return ResponseEntity.ok(new SuccessResponse("Order status updated successfully"));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Order not found with ID: " + id));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid status: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }
    // ==================== MENU ENDPOINTS (UNCHANGED) ====================
    @PostMapping("/menu")
    public ResponseEntity<?> addMenuItem(@Valid @RequestBody MenuItemDTO menuItemDTO, HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} adding menu item: {}", email, menuItemDTO.getName());
        try {
            MenuItemDTO savedItem = menuService.addMenuItem(menuItemDTO);
            return ResponseEntity.ok(savedItem);
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to add menu item: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }
    @PutMapping("/menu/{id}")
    public ResponseEntity<?> updateMenuItem(@PathVariable Long id,
                                            @Valid @RequestBody MenuItemDTO menuItemDTO,
                                            HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} updating menu item ID: {}", email, id);
        if (menuItemDTO.getId() == null || !id.equals(menuItemDTO.getId())) {
            menuItemDTO.setId(id);
        }
        try {
            MenuItemDTO updatedItem = menuService.updateMenuItem(id, menuItemDTO);
            return ResponseEntity.ok(updatedItem);
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to update menu item: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }
    @DeleteMapping("/menu/{id}")
    public ResponseEntity<?> deleteMenuItem(@PathVariable Long id, HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} deleting menu item ID: {}", email, id);
        try {
            boolean deleted = menuService.deleteMenuItem(id);
            if (!deleted) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Menu item not found with ID: " + id));
            }
            return ResponseEntity.ok(new SuccessResponse("Menu item deleted successfully"));
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to delete menu item: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }
    @GetMapping("/menu")
    public ResponseEntity<List<MenuItemDTO>> getAllMenuItems(HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} fetching all menu items", email);
        return ResponseEntity.ok(menuService.getAllMenuItems());
    }
    @GetMapping("/menu/categories")
    public ResponseEntity<List<CategoryDTO>> getAllCategories(HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} fetching all categories", email);
        try {
            return ResponseEntity.ok(menuService.getAllCategories());
        } catch (Exception e) {
            logger.error("Failed to fetch categories", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }
    @PostMapping("/menu/categories")
    public ResponseEntity<?> addCategory(@Valid @RequestBody CategoryDTO categoryDTO, HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} adding category: {}", email, categoryDTO.getName());
        try {
            CategoryDTO saved = menuService.addCategory(categoryDTO.getName());
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to add category: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }
    @PutMapping("/menu/categories/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable Long id,
                                            @Valid @RequestBody CategoryDTO categoryDTO,
                                            HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} updating category ID: {} to {}", email, id, categoryDTO.getName());
        try {
            boolean updated = menuService.updateCategory(id, categoryDTO.getName());
            if (!updated) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Category not found with ID: " + id));
            }
            return ResponseEntity.ok(new SuccessResponse("Category updated successfully"));
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to update category: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }
    @DeleteMapping("/menu/categories/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id, HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} deleting category ID: {}", email, id);
        try {
            boolean deleted = menuService.deleteCategory(id);
            if (!deleted) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Category not found with ID: " + id));
            }
            return ResponseEntity.ok(new SuccessResponse("Category deleted successfully"));
        } catch (IllegalStateException e) {
            logger.warn("Cannot delete category with items: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }
    @PostMapping("/notifications")
    public ResponseEntity<SentNotificationDTO> sendNotification(@RequestBody Map<String, Object> payload,
                                                                HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} sending notification", email);
        SentNotificationDTO dto = notificationService.sendNotification(payload);
        return ResponseEntity.ok(dto);
    }
    @GetMapping("/notifications-history")
    public ResponseEntity<List<SentNotificationDTO>> getNotificationHistory(HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} fetching notification history", email);
        return ResponseEntity.ok(notificationService.getHistory());
    }
    // NEW: Get partner applications
    @GetMapping("/partners")
    public ResponseEntity<?> getPartnerApplications(@RequestParam(defaultValue = "ALL") String status,
                                                    HttpServletRequest request) {
        String token = extractToken(request);
        String adminEmail = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} fetching partner applications with status: {}", adminEmail, status);
        try {
            List<PartnerApplicationDTO> applications = partnerService.getApplicationsByStatus(status);
            return ResponseEntity.ok(applications);
        } catch (Exception e) {
            logger.error("Error fetching partner applications: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to fetch partner applications: " + e.getMessage()));
        }
    }
    // NEW: Approve partner application
    @PutMapping("/partners/{id}/approve")
    public ResponseEntity<?> approvePartnerApplication(@PathVariable Long id, HttpServletRequest request) {
        String token = extractToken(request);
        String adminEmail = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} approving partner application ID: {}", adminEmail, id);
        try {
            partnerService.approveApplication(id);
            return ResponseEntity.ok(new SuccessResponse("Application approved and restaurant owner account created"));
        } catch (IllegalArgumentException e) {
            logger.warn("Approval failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error approving application: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to approve application: " + e.getMessage()));
        }
    }
    // NEW: Reject partner application
    @PutMapping("/partners/{id}/reject")
    public ResponseEntity<?> rejectPartnerApplication(@PathVariable Long id,
                                                      @RequestBody Map<String, String> body,
                                                      HttpServletRequest request) {
        String token = extractToken(request);
        String adminEmail = jwtUtil.getEmailFromToken(token);
        String reason = body.getOrDefault("reason", "No reason provided");
        logger.info("Admin {} rejecting partner application ID: {} with reason: {}", adminEmail, id, reason);
        try {
            partnerService.rejectApplication(id, reason);
            return ResponseEntity.ok(new SuccessResponse("Application rejected successfully"));
        } catch (IllegalArgumentException e) {
            logger.warn("Rejection failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error rejecting application: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to reject application: " + e.getMessage()));
        }
    }
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        logger.warn("Missing or invalid Authorization header");
        throw new IllegalArgumentException("Missing or invalid Authorization header");
    }
    // Inner DTO classes
    public static class UpdateUserDTO {
        private String role;
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
    public static class ErrorResponse {
        private String message;
        public ErrorResponse(String message) { this.message = message; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
    public static class SuccessResponse {
        private String message;
        public SuccessResponse(String message) { this.message = message; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}