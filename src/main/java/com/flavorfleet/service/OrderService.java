package com.flavorfleet.service;

import com.flavorfleet.dto.OrderDTO;
import com.flavorfleet.dto.CartItemDTO;
import com.flavorfleet.entity.CartItem;
import com.flavorfleet.entity.Order;
import com.flavorfleet.entity.User;
import com.flavorfleet.repository.OrderRepository;
import com.flavorfleet.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final NotificationService notificationService;

    public OrderService(OrderRepository orderRepository, UserRepository userRepository, UserService userService, NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<Order> getOrders(String email) {
        User user = userService.findByEmail(email);
        if (user == null) {
            logger.warn("No user found for email: {}", email);
            return List.of();
        }
        List<Order> orders = orderRepository.findByUser(user);
        logger.info("Fetched {} orders for email: {}", orders.size(), email);
        return orders;
    }

    @Transactional
    public Order saveOrder(OrderDTO orderDTO, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    logger.error("User not found for email: {}", email);
                    return new RuntimeException("User not found for email: " + email);
                });

        // Create a new Order entity
        Order order = new Order();
        order.setUser(user);
        order.setTotalPrice(orderDTO.getTotalPrice());
        order.setAddressLine1(orderDTO.getAddressLine1());
        order.setAddressLine2(orderDTO.getAddressLine2());
        order.setCity(orderDTO.getCity());
        order.setPincode(orderDTO.getPincode());

        // Handle null items by initializing an empty list
        List<CartItemDTO> items = orderDTO.getItems() != null ? orderDTO.getItems() : new ArrayList<>();

        // Validate that the order has at least one item
        if (items.isEmpty()) {
            logger.error("Order cannot be placed with no items for email: {}", email);
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        // Create new CartItem entities for the order
        items.forEach(itemDTO -> {
            CartItem cartItem = new CartItem(
                    itemDTO.getItemId(),
                    itemDTO.getName(),
                    itemDTO.getPrice(),
                    itemDTO.getQuantity(),
                    itemDTO.getImage(),
                    user, // Set the user to the same user as the order
                    order
            );
            order.addItem(cartItem);
        });

        // Save the order
        Order savedOrder = orderRepository.save(order);
        logger.info("Order saved successfully with ID: {} for email: {}", savedOrder.getId(), email);

        // Send notification
        notificationService.sendOrderUpdate(savedOrder, "Pending");

        return savedOrder;
    }
}