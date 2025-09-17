package com.flavorfleet.controller;

import com.flavorfleet.config.JwtUtil;
import com.flavorfleet.dto.CartItemDTO;
import com.flavorfleet.dto.CategoryDTO;
import com.flavorfleet.dto.MenuItemDTO;
import com.flavorfleet.dto.OrderDTO;
import com.flavorfleet.dto.SentNotificationDTO;
import com.flavorfleet.entity.Order;
import com.flavorfleet.entity.User;
import com.flavorfleet.service.MenuService;
import com.flavorfleet.service.NotificationService;
import com.flavorfleet.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final MenuService menuService;
    private final NotificationService notificationService;
    private final JwtUtil jwtUtil;

    public AdminController(UserService userService, MenuService menuService, NotificationService notificationService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.menuService = menuService;
        this.notificationService = notificationService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers(HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Fetching all users by admin: {}", email);
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody UpdateUserDTO dto, HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} updating user with ID: {}", email, id);
        try {
            boolean updated = userService.updateUser(id, dto.getRole());
            if (updated) {
                return ResponseEntity.ok(new SuccessResponse("User updated successfully. Please ask the user to re-login for changes to take effect."));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("User not found with ID: " + id));
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to update user: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    public static class UpdateUserDTO {
        private String role;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
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

    @GetMapping("/orders")
    public ResponseEntity<List<OrderDTO>> getAllOrders(HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} fetching all orders", email);
        List<Order> orders = userService.getAllOrders();
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
    }

    @PutMapping("/orders/{id}")
    public ResponseEntity<?> updateOrderStatus(@PathVariable Long id, @RequestBody Order order, HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} updating order status for order ID: {}", email, id);
        try {
            boolean updated = userService.updateOrderStatus(id, order.getStatus());
            if (updated) {
                return ResponseEntity.ok(new SuccessResponse("Order status updated successfully"));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Order not found with ID: " + id));
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to update order status: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

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
    public ResponseEntity<?> updateMenuItem(@PathVariable Long id, @Valid @RequestBody MenuItemDTO menuItemDTO, HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} updating menu item with ID: {}", email, id);

        if (menuItemDTO.getId() == null || !id.equals(menuItemDTO.getId())) {
            logger.info("DTO ID {} is null or does not match path ID {}, using path ID", menuItemDTO.getId(), id);
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
        logger.info("Admin {} deleting menu item with ID: {}", email, id);
        try {
            boolean deleted = menuService.deleteMenuItem(id);
            if (!deleted) {
                logger.warn("Menu item not found with ID: {}", id);
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
        List<MenuItemDTO> menuItems = menuService.getAllMenuItems();
        return ResponseEntity.ok(menuItems);
    }

    @GetMapping("/menu/categories")
    public ResponseEntity<List<CategoryDTO>> getAllCategories(HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} fetching all categories", email);
        try {
            List<CategoryDTO> categories = menuService.getAllCategories();
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            logger.error("Failed to fetch categories", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of());
        }
    }

    @PostMapping("/menu/categories")
    public ResponseEntity<?> addCategory(@Valid @RequestBody CategoryDTO categoryDTO, HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} adding category: {}", email, categoryDTO.getName());
        try {
            CategoryDTO savedCategory = menuService.addCategory(categoryDTO.getName());
            return ResponseEntity.ok(savedCategory);
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to add category: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PutMapping("/menu/categories/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryDTO categoryDTO, HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        logger.info("Admin {} updating category with ID: {} to {}", email, id, categoryDTO.getName());
        try {
            boolean updated = menuService.updateCategory(id, categoryDTO.getName());
            if (!updated) {
                logger.warn("Category not found with ID: {}", id);
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
        logger.info("Admin {} deleting category with ID: {}", email, id);
        try {
            boolean deleted = menuService.deleteCategory(id);
            if (!deleted) {
                logger.warn("Category not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Category not found with ID: " + id));
            }
            return ResponseEntity.ok(new SuccessResponse("Category deleted successfully"));
        } catch (IllegalStateException e) {
            logger.warn("Failed to delete category: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/notifications")
    public ResponseEntity<SentNotificationDTO> sendNotification(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
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
        List<SentNotificationDTO> history = notificationService.getHistory();
        return ResponseEntity.ok(history);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        logger.warn("Missing or invalid Authorization header");
        throw new IllegalArgumentException("Missing or invalid Authorization header");
    }

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