package com.flavorfleet.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "menu_items")
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double price;

    @Column(length = 500)
    private String description;

    private String image;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String type; // New field for Veg/Non-Veg

    public MenuItem() {}

    public MenuItem(String name, Double price, String description, String image, Category category, String type) {
        this.name = name;
        this.price = price;
        this.description = description;
        this.image = image;
        this.category = category;
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
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}