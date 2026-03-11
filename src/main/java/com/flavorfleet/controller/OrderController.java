package com.flavorfleet.controller;

import com.flavorfleet.config.JwtUtil;
import com.flavorfleet.dto.CartItemDTO;
import com.flavorfleet.dto.OrderDTO;
import com.flavorfleet.entity.Order;
import com.flavorfleet.service.OrderService;
import com.flavorfleet.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "http://localhost:8484")
public class OrderController {
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public OrderController(OrderService orderService, UserService userService, JwtUtil jwtUtil) {
        this.orderService = orderService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public ResponseEntity<List<OrderDTO>> getOrders(HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);

        List<Order> orders = orderService.getOrders(email);
        List<OrderDTO> orderDTOs = orders.stream()
                .map(this::mapToOrderDTO)
                .collect(Collectors.toList());

        logger.info("Orders fetched successfully for email: {}", email);
        return ResponseEntity.ok(orderDTOs);
    }

    @PostMapping
    public ResponseEntity<?> createOrder(@Valid @RequestBody OrderDTO orderDTO, HttpServletRequest request) {
        try {
            String token = extractToken(request);
            String email = jwtUtil.getEmailFromToken(token);

            Order savedOrder = orderService.saveOrder(orderDTO, email);

            OrderDTO savedOrderDTO = mapToOrderDTO(savedOrder);

            logger.info("Order created successfully with ID: {} for email: {}", savedOrder.getId(), email);
            return ResponseEntity.ok(savedOrderDTO);
        } catch (IllegalArgumentException e) {
            logger.warn("Order creation failed due to validation: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new AdminController.ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Order creation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AdminController.ErrorResponse("Failed to create order: " + e.getMessage()));
        }
    }

    private OrderDTO mapToOrderDTO(Order order) {
        return new OrderDTO(
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
                order.getPincode()
        );
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Missing or invalid Authorization header");
    }
}