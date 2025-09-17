package com.flavorfleet.controller;

import com.flavorfleet.config.JwtUtil;
import com.flavorfleet.dto.CartItemDTO;
import com.flavorfleet.dto.OrderDTO;
import com.flavorfleet.entity.Order;
import com.flavorfleet.service.OrderService;
import com.flavorfleet.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
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
        logger.info("Orders fetched successfully for email: {}", email);
        return ResponseEntity.ok(orderDTOs);
    }

    @PostMapping
    public ResponseEntity<OrderDTO> createOrder(@RequestBody OrderDTO orderDTO, HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);

        // Delegate to the service to create the Order entity
        Order savedOrder = orderService.saveOrder(orderDTO, email);

        // Clear the cart after order creation
        userService.clearCart(email);

        // Map the saved Order to OrderDTO for the response
        OrderDTO savedOrderDTO = new OrderDTO(
                savedOrder.getId(),
                savedOrder.getUser().getEmail(),
                savedOrder.getUser().getName(),
                savedOrder.getTotalPrice(),
                savedOrder.getStatus(),
                savedOrder.getCreatedAt(),
                savedOrder.getItems().stream()
                        .map(item -> new CartItemDTO(
                                item.getId(),
                                item.getItemId(),
                                item.getName(),
                                item.getPrice(),
                                item.getQuantity(),
                                item.getImage()))
                        .collect(Collectors.toList()),
                savedOrder.getAddressLine1(),
                savedOrder.getAddressLine2(),
                savedOrder.getCity(),
                savedOrder.getPincode());
        logger.info("Order created successfully with ID: {} for email: {}", savedOrder.getId(), email);
        return ResponseEntity.ok(savedOrderDTO);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new RuntimeException("Missing or invalid Authorization header");
    }
}