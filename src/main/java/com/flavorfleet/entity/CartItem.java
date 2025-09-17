package com.flavorfleet.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Entity
@Table(name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id")
    @NotNull(message = "Item ID cannot be null")
    private Long itemId;

    @Column(name = "name")
    @NotNull(message = "Name cannot be null")
    private String name;

    @Column(name = "price")
    @NotNull(message = "Price cannot be null")
    @Positive(message = "Price must be positive")
    private Double price;

    @Column(name = "quantity")
    @NotNull(message = "Quantity cannot be null")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    @Column(name = "image")
    private String image;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true) // Allow user_id to be nullable
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @JsonBackReference
    private Order order;

    public CartItem() {
    }

    public CartItem(Long itemId, String name, Double price, Integer quantity, String image, User user, Order order) {
        this.itemId = itemId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.image = image;
        this.user = user;
        this.order = order;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
}