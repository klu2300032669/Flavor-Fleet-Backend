
package com.flavorfleet.controller;

import com.flavorfleet.config.JwtUtil;
import com.flavorfleet.entity.FavoriteItem;
import com.flavorfleet.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/favorites")
@CrossOrigin(origins = "http://localhost:8484") // Updated to match frontend port
public class FavoritesController {
    private static final Logger logger = LoggerFactory.getLogger(FavoritesController.class);

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public FavoritesController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public ResponseEntity<List<FavoriteItem>> getFavorites(HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        List<FavoriteItem> favorites = userService.getFavoriteItems(email);
        logger.info("Favorites fetched successfully for email: {}", email);
        return ResponseEntity.ok(favorites);
    }

    @PostMapping
    public ResponseEntity<FavoriteItem> addToFavorites(@RequestBody FavoriteItem favoriteItem, HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        FavoriteItem savedItem = userService.addToFavorites(email, favoriteItem);
        logger.info("Item added to favorites with ID: {} for email: {}", savedItem.getId(), email);
        return ResponseEntity.ok(savedItem);
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> removeFromFavorites(@PathVariable Long itemId, HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtil.getEmailFromToken(token);
        userService.removeFromFavorites(email, itemId);
        logger.info("Item removed from favorites with ID: {} for email: {}", itemId, email);
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