package com.flavorfleet.controller;

import com.flavorfleet.config.JwtUtil;
import com.flavorfleet.dto.CartItemDTO;
import com.flavorfleet.entity.CartItem;
import com.flavorfleet.entity.User;
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
        List<CartItemDTO> dtos = cartItems.stream()
                .map(item -> new CartItemDTO(
                        item.getId(),
                        item.getItemId(),
                        item.getName(),
                        item.getPrice(),
                        item.getQuantity(),
                        item.getImage()))
                .collect(Collectors.toList());

        logger.info("Cart fetched successfully for email: {} ({} items)", email, dtos.size());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<?> addToCart(@Valid @RequestBody CartItemDTO cartItemDTO, HttpServletRequest request) {
        try {
            String token = extractToken(request);
            String email = jwtUtil.getEmailFromToken(token);

            if (cartItemDTO.getQuantity() <= 0) {
                return ResponseEntity.badRequest().body(new AdminController.ErrorResponse("Quantity must be positive"));
            }
            if (cartItemDTO.getPrice() == null || cartItemDTO.getPrice() <= 0) {
                return ResponseEntity.badRequest().body(new AdminController.ErrorResponse("Price must be positive"));
            }

            CartItem cartItem = new CartItem();
            cartItem.setItemId(cartItemDTO.getItemId());
            cartItem.setName(cartItemDTO.getName());
            cartItem.setPrice(cartItemDTO.getPrice());
            cartItem.setQuantity(cartItemDTO.getQuantity());
            cartItem.setImage(cartItemDTO.getImage());

            CartItem saved = userService.addToCart(email, cartItem);

            CartItemDTO response = new CartItemDTO(
                    saved.getId(),
                    saved.getItemId(),
                    saved.getName(),
                    saved.getPrice(),
                    saved.getQuantity(),
                    saved.getImage());

            logger.info("Item added to cart ID: {} for email: {}", saved.getId(), email);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to add to cart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AdminController.ErrorResponse("Failed to add item: " + e.getMessage()));
        }
    }

    @PutMapping
    public ResponseEntity<?> updateCartItem(@Valid @RequestBody CartItemDTO cartItemDTO, HttpServletRequest request) {
        try {
            String token = extractToken(request);
            String email = jwtUtil.getEmailFromToken(token);

            if (cartItemDTO.getId() == null) {
                return ResponseEntity.badRequest().body(new AdminController.ErrorResponse("Cart item ID required"));
            }
            if (cartItemDTO.getQuantity() <= 0) {
                return ResponseEntity.badRequest().body(new AdminController.ErrorResponse("Quantity must be positive"));
            }

            CartItem update = new CartItem();
            update.setId(cartItemDTO.getId());
            update.setItemId(cartItemDTO.getItemId());
            update.setName(cartItemDTO.getName());
            update.setPrice(cartItemDTO.getPrice());
            update.setQuantity(cartItemDTO.getQuantity());
            update.setImage(cartItemDTO.getImage());

            CartItem updated = userService.updateCartItem(email, update);

            CartItemDTO response = new CartItemDTO(
                    updated.getId(),
                    updated.getItemId(),
                    updated.getName(),
                    updated.getPrice(),
                    updated.getQuantity(),
                    updated.getImage());

            logger.info("Cart item updated ID: {} for email: {}", updated.getId(), email);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update cart item", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AdminController.ErrorResponse("Failed to update item: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> removeFromCart(@PathVariable Long itemId, HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);

        boolean removed = userService.removeFromCart(email, itemId);
        if (removed) {
            logger.info("Item removed from cart ID: {} for email: {}", itemId, email);
            return ResponseEntity.noContent().build();
        } else {
            logger.warn("Item not found in cart ID: {} for email: {}", itemId, email);
            return ResponseEntity.notFound().build();
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
        throw new IllegalArgumentException("Missing or invalid Authorization header");
    }
}