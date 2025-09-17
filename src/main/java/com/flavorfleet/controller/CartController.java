package com.flavorfleet.controller;

import com.flavorfleet.config.JwtUtil;
import com.flavorfleet.dto.CartItemDTO;
import com.flavorfleet.entity.CartItem;
import com.flavorfleet.entity.User;
import com.flavorfleet.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cart")
@CrossOrigin(origins = "http://localhost:8484")
public class CartController {
    private static final Logger logger = LoggerFactory.getLogger(CartController.class);

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public CartController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public ResponseEntity<List<CartItemDTO>> getCart(HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        List<CartItem> cartItems = userService.getCartItems(email);
        List<CartItemDTO> cartItemDTOs = cartItems.stream()
                .map(item -> new CartItemDTO(
                        item.getId(),
                        item.getItemId(),
                        item.getName(),
                        item.getPrice(),
                        item.getQuantity(),
                        item.getImage()))
                .collect(Collectors.toList());
        logger.info("Cart fetched successfully for email: {}", email);
        return ResponseEntity.ok(cartItemDTOs);
    }

    @PostMapping
    public ResponseEntity<CartItemDTO> addToCart(@RequestBody CartItemDTO cartItemDTO, HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        
        // Fetch the User entity using the email
        User user = userService.findByEmail(email);
        if (user == null) {
            logger.error("User not found for email: {}", email);
            return ResponseEntity.status(404).body(null); // Or throw an exception
        }

        // Create the CartItem and set the User
        CartItem cartItem = new CartItem();
        cartItem.setItemId(cartItemDTO.getItemId());
        cartItem.setName(cartItemDTO.getName());
        cartItem.setPrice(cartItemDTO.getPrice());
        cartItem.setQuantity(cartItemDTO.getQuantity());
        cartItem.setImage(cartItemDTO.getImage());
        cartItem.setUser(user); // Set the User here

        CartItem savedItem = userService.addToCart(email, cartItem);
        CartItemDTO savedItemDTO = new CartItemDTO(
                savedItem.getId(),
                savedItem.getItemId(),
                savedItem.getName(),
                savedItem.getPrice(),
                savedItem.getQuantity(),
                savedItem.getImage());
        logger.info("Item added to cart with ID: {} for email: {}", savedItem.getId(), email);
        return ResponseEntity.ok(savedItemDTO);
    }

    @PutMapping
    public ResponseEntity<CartItemDTO> updateCartItem(@RequestBody CartItemDTO cartItemDTO, HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        
        // Fetch the User entity using the email
        User user = userService.findByEmail(email);
        if (user == null) {
            logger.error("User not found for email: {}", email);
            return ResponseEntity.status(404).body(null); // Or throw an exception
        }

        CartItem cartItem = new CartItem();
        cartItem.setId(cartItemDTO.getId());
        cartItem.setItemId(cartItemDTO.getItemId());
        cartItem.setName(cartItemDTO.getName());
        cartItem.setPrice(cartItemDTO.getPrice());
        cartItem.setQuantity(cartItemDTO.getQuantity());
        cartItem.setImage(cartItemDTO.getImage());
        cartItem.setUser(user); // Set the User here

        CartItem updatedItem = userService.updateCartItem(email, cartItem);
        CartItemDTO updatedItemDTO = new CartItemDTO(
                updatedItem.getId(),
                updatedItem.getItemId(),
                updatedItem.getName(),
                updatedItem.getPrice(),
                updatedItem.getQuantity(),
                updatedItem.getImage());
        logger.info("Cart item updated with ID: {} for email: {}", updatedItem.getId(), email);
        return ResponseEntity.ok(updatedItemDTO);
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> removeFromCart(@PathVariable Long itemId, HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        boolean removed = userService.removeFromCart(email, itemId);
        if (removed) {
            logger.info("Item removed from cart with ID: {} for email: {}", itemId, email);
            return ResponseEntity.noContent().build();
        } else {
            logger.warn("Item not found in cart with ID: {} for email: {}", itemId, email);
            return ResponseEntity.status(404).build();
        }
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        userService.clearCart(email);
        logger.info("Cart cleared successfully for email: {}", email);
        return ResponseEntity.noContent().build();
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new RuntimeException("Missing or invalid Authorization header");
    }
}