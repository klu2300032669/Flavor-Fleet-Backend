package com.flavorfleet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class MenuItemDTO {

    private Long id;

    @NotBlank(message = "Menu item name is required")
    @Size(max = 100, message = "Menu item name must not exceed 100 characters")
    private String name;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private Double price;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private String image;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    private String categoryName;

    @NotBlank(message = "Type is required")
    @Size(max = 10, message = "Type must be 'Veg' or 'Non-Veg'")
    private String type;

    public MenuItemDTO() {}

    public MenuItemDTO(Long id, String name, Double price, String description, String image, Long categoryId, String categoryName, String type) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.description = description;
        this.image = image;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.type = type;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}