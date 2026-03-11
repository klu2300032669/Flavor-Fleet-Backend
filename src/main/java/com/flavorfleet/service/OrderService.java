package com.flavorfleet.service;

import com.flavorfleet.dto.CartItemDTO;
import com.flavorfleet.dto.OrderDTO;
import com.flavorfleet.entity.CartItem;
import com.flavorfleet.entity.Order;
import com.flavorfleet.entity.User;
import com.flavorfleet.repository.OrderRepository;
import com.flavorfleet.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final NotificationService notificationService;

    public OrderService(OrderRepository orderRepository,
                        UserRepository userRepository,
                        UserService userService,
                        NotificationService notificationService) {
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

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        logger.info("Fetching all orders for admin");
        return orderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByUserId(Long userId) {
        logger.info("Fetching orders for user ID: {}", userId);
        return orderRepository.findByUserId(userId);
    }

    @Transactional
    public void deleteOrder(Long orderId) {
        logger.info("Deleting order with ID: {}", orderId);
        if (!orderRepository.existsById(orderId)) {
            logger.warn("Order not found for deletion: {}", orderId);
            throw new IllegalArgumentException("Order not found with ID: " + orderId);
        }
        orderRepository.deleteById(orderId);
    }

    @Transactional(readOnly = true)
    public int getTotalOrders() {
        long count = orderRepository.count();
        logger.info("Total orders in system: {}", count);
        return (int) count;
    }

    @Transactional
    public Order saveOrder(OrderDTO orderDTO, String email) {
        // 1. Validate input
        if (orderDTO == null) {
            throw new IllegalArgumentException("Order data cannot be null");
        }

        List<CartItemDTO> items = orderDTO.getItems();
        if (items == null || items.isEmpty()) {
            logger.error("Order cannot be placed with no items for email: {}", email);
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        // 2. Validate prices and quantities
        BigDecimal calculatedTotal = BigDecimal.ZERO;
        for (CartItemDTO item : items) {
            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Item quantity must be positive: " + item.getName());
            }
            if (item.getPrice() == null || item.getPrice() <= 0) {
                throw new IllegalArgumentException("Item price must be positive: " + item.getName());
            }
            BigDecimal itemTotal = BigDecimal.valueOf(item.getPrice()).multiply(BigDecimal.valueOf(item.getQuantity()));
            calculatedTotal = calculatedTotal.add(itemTotal);
        }

        // 3. Prevent price tampering - recalculate total from items
        if (orderDTO.getTotalPrice() == null || 
            BigDecimal.valueOf(orderDTO.getTotalPrice()).compareTo(calculatedTotal) != 0) {
            logger.warn("Price mismatch detected for email: {}. Client sent: {}, calculated: {}", 
                        email, orderDTO.getTotalPrice(), calculatedTotal);
            orderDTO.setTotalPrice(calculatedTotal.doubleValue()); // Trust server calculation
        }

        // 4. Find user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    logger.error("User not found for email: {}", email);
                    return new RuntimeException("User not found for email: " + email);
                });

        // 5. Create order
        Order order = new Order();
        order.setUser(user);
        order.setTotalPrice(calculatedTotal.doubleValue());
        order.setAddressLine1(orderDTO.getAddressLine1());
        order.setAddressLine2(orderDTO.getAddressLine2());
        order.setCity(orderDTO.getCity());
        order.setPincode(orderDTO.getPincode());
        order.setStatus("PENDING"); // Default status
        order.setCreatedAt(LocalDateTime.now());

        // 6. Add items (server-trusted data)
        items.forEach(itemDTO -> {
            CartItem cartItem = new CartItem(
                    itemDTO.getItemId(),
                    itemDTO.getName(),
                    itemDTO.getPrice(),
                    itemDTO.getQuantity(),
                    itemDTO.getImage(),
                    user,
                    order
            );
            order.addItem(cartItem);
        });

        // 7. Save and notify
        Order savedOrder = orderRepository.saveAndFlush(order);
        logger.info("Order saved successfully with ID: {} for email: {}", savedOrder.getId(), email);

        notificationService.sendOrderUpdate(savedOrder, "PENDING");

        // 8. Clear cart after successful order
        userService.clearCart(email);

        return savedOrder;
    }

    @Transactional(readOnly = true)
    public List<Order> getFilteredOrdersForStats(String timeRange) {
        logger.info("Fetching filtered orders for stats with timeRange: {}", timeRange);
        List<Order> allOrders = orderRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate;

        if ("7d".equals(timeRange)) {
            startDate = now.minusDays(7);
        } else if ("30d".equals(timeRange)) {
            startDate = now.minusDays(30);
        } else { // "all" or invalid
            return allOrders;
        }

        return allOrders.stream()
                .filter(order -> order.getCreatedAt() != null && !order.getCreatedAt().isBefore(startDate))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalRevenueForOrders(List<Order> orders) {
        return orders.stream()
                .map(Order::getTotalPrice)
                .filter(Objects::nonNull)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getOrderStatusDistribution(List<Order> orders) {
        return orders.stream()
                .collect(Collectors.groupingBy(
                        order -> order.getStatus() != null ? order.getStatus() : "UNKNOWN",
                        Collectors.counting()
                ));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countOrdersByStatus(String timeRange) {
        List<Order> filteredOrders = getFilteredOrdersForStats(timeRange);
        return getOrderStatusDistribution(filteredOrders);
    }
}