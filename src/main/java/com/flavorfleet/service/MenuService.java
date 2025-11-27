package com.flavorfleet.service;

import com.flavorfleet.dto.CategoryDTO;
import com.flavorfleet.dto.MenuItemDTO;
import com.flavorfleet.entity.Category;
import com.flavorfleet.entity.MenuItem;
import com.flavorfleet.repository.CategoryRepository;
import com.flavorfleet.repository.MenuItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MenuService {
    private static final Logger logger = LoggerFactory.getLogger(MenuService.class);
    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;

    public MenuService(MenuItemRepository menuItemRepository, CategoryRepository categoryRepository) {
        this.menuItemRepository = menuItemRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<MenuItemDTO> getAllMenuItems() {
        logger.info("Fetching all menu items");
        return menuItemRepository.findAll().stream()
                .filter(item -> item.getCategory() == null || !item.getCategory().isDeleted())
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // NEW: Added for type and price filtering
    @Transactional(readOnly = true)
    public List<MenuItemDTO> getMenuItemsByTypeAndPrice(String type, Double minPrice, Double maxPrice) {
        logger.info("Fetching menu items for type: {}, minPrice: {}, maxPrice: {}", type, minPrice, maxPrice);
        return menuItemRepository.findByTypeAndPrice(type, minPrice, maxPrice).stream()
                .filter(item -> item.getCategory() == null || !item.getCategory().isDeleted())
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MenuItemDTO> getMenuItemsByCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Category cannot be null or empty");
        }
        logger.info("Fetching menu items for category: {}", category);
        return menuItemRepository.findByCategoryName(category).stream()
                .filter(item -> item.getCategory() == null || !item.getCategory().isDeleted())
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CategoryDTO> getAllCategories() {
        logger.info("Fetching all categories");
        return categoryRepository.findAll().stream()
                .map(category -> new CategoryDTO(category.getId(), category.getName()))
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoryDTO addCategory(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name is required");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("Category name must not exceed 100 characters");
        }
        logger.info("Adding category: {}", name);
        if (categoryRepository.findByName(name).isPresent()) {
            throw new IllegalArgumentException("Category already exists: " + name);
        }
        Category category = new Category(name);
        Category savedCategory = categoryRepository.save(category);
        return new CategoryDTO(savedCategory.getId(), savedCategory.getName());
    }

    @Transactional
    public boolean updateCategory(Long id, String newName) {
        if (id == null) {
            throw new IllegalArgumentException("Category ID is required");
        }
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name cannot be empty");
        }
        if (newName.length() > 100) {
            throw new IllegalArgumentException("Category name must not exceed 100 characters");
        }
        logger.info("Updating category with ID: {} to {}", id, newName);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with ID: " + id));
        if (category.isDeleted()) {
            throw new IllegalArgumentException("Cannot update deleted category: " + category.getName());
        }
        if (categoryRepository.findByName(newName).isPresent()) {
            throw new IllegalArgumentException("Category already exists: " + newName);
        }
        category.setName(newName);
        categoryRepository.save(category);
        return true;
    }

    @Transactional
    public boolean deleteCategory(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Category ID is required");
        }
        logger.info("Soft deleting category with ID: {}", id);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with ID: " + id));
        if (!category.getMenuItems().isEmpty()) {
            throw new IllegalStateException("Cannot delete category with associated menu items");
        }
        category.setDeleted(true);
        categoryRepository.save(category);
        return true;
    }

    @Transactional
    public MenuItemDTO addMenuItem(MenuItemDTO menuItemDTO) {
        validateMenuItemDTO(menuItemDTO);
        logger.info("Adding menu item: {}", menuItemDTO.getName());
        Category category = categoryRepository.findById(menuItemDTO.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found with ID: " + menuItemDTO.getCategoryId()));
        if (category.isDeleted()) {
            throw new IllegalArgumentException("Cannot add menu item to deleted category: " + category.getName());
        }
        MenuItem menuItem = new MenuItem(
                menuItemDTO.getName(),
                menuItemDTO.getPrice(),
                menuItemDTO.getDescription(),
                menuItemDTO.getImage(),
                category,
                menuItemDTO.getType()
        );
        MenuItem savedItem = menuItemRepository.save(menuItem);
        return toDTO(savedItem);
    }

    @Transactional
    public MenuItemDTO updateMenuItem(Long id, MenuItemDTO menuItemDTO) {
        if (id == null) {
            throw new IllegalArgumentException("Menu item ID is required");
        }
        validateMenuItemDTO(menuItemDTO);
        logger.info("Updating menu item with ID: {}", id);
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found with ID: " + id));
        Category category = categoryRepository.findById(menuItemDTO.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found with ID: " + menuItemDTO.getCategoryId()));
        if (category.isDeleted()) {
            throw new IllegalArgumentException("Cannot update menu item to deleted category: " + category.getName());
        }
        menuItem.setName(menuItemDTO.getName());
        menuItem.setPrice(menuItemDTO.getPrice());
        menuItem.setDescription(menuItemDTO.getDescription());
        menuItem.setImage(menuItemDTO.getImage());
        menuItem.setCategory(category);
        menuItem.setType(menuItemDTO.getType());
        MenuItem updatedItem = menuItemRepository.save(menuItem);
        return toDTO(updatedItem);
    }

    @Transactional
    public boolean deleteMenuItem(Long id) {
        logger.info("Deleting menu item with ID: {}", id);
        if (!menuItemRepository.existsById(id)) {
            return false;
        }
        menuItemRepository.deleteById(id);
        return true;
    }

    private void validateMenuItemDTO(MenuItemDTO menuItemDTO) {
        if (menuItemDTO.getName() == null || menuItemDTO.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Menu item name is required");
        }
        if (menuItemDTO.getPrice() == null || menuItemDTO.getPrice() <= 0) {
            throw new IllegalArgumentException("Price must be a positive number");
        }
        if (menuItemDTO.getCategoryId() == null) {
            throw new IllegalArgumentException("Category ID is required");
        }
        if (menuItemDTO.getType() == null || (!menuItemDTO.getType().equals("Veg") && !menuItemDTO.getType().equals("Non-Veg"))) {
            throw new IllegalArgumentException("Type must be 'Veg' or 'Non-Veg'");
        }
    }

    private MenuItemDTO toDTO(MenuItem menuItem) {
        Category cat = menuItem.getCategory();
        return new MenuItemDTO(
                menuItem.getId(),
                menuItem.getName(),
                menuItem.getPrice(),
                menuItem.getDescription(),
                menuItem.getImage(),
                cat != null ? cat.getId() : null,
                cat != null ? cat.getName() : "Unknown",
                menuItem.getType()
        );
    }
}