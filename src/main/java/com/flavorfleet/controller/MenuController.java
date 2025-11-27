package com.flavorfleet.controller;

import com.flavorfleet.dto.CategoryDTO;
import com.flavorfleet.dto.MenuItemDTO;
import com.flavorfleet.service.MenuService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/menu")
@CrossOrigin(origins = "http://localhost:8484")
public class MenuController {

    private static final Logger logger = LoggerFactory.getLogger(MenuController.class);
    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDTO>> getAllCategories() {
        logger.info("Fetching all menu categories");
        try {
            List<CategoryDTO> categories = menuService.getAllCategories();
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            logger.error("Failed to fetch categories", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.emptyList());
        }
    }

    @GetMapping
    public ResponseEntity<List<MenuItemDTO>> getMenuItems(@RequestParam(required = false) String category) {
        logger.info("Fetching menu items" + (category != null ? " for category: " + category : ""));
        try {
            List<MenuItemDTO> menuItems = category != null ? 
                menuService.getMenuItemsByCategory(category) : 
                menuService.getAllMenuItems();
            if (category != null && menuItems.isEmpty()) {
                logger.info("No items found for category: {}", category);
                return ResponseEntity.ok(Collections.emptyList());
            }
            return ResponseEntity.ok(menuItems);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.emptyList());
        } catch (Exception e) {
            logger.error("Failed to fetch menu items", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.emptyList());
        }
    }
}