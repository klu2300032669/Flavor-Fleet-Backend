package com.flavorfleet.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "favorite_items")
public class FavoriteItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id")
    @NotNull(message = "Item ID cannot be null")
    private Long itemId;

    @Column(name = "name")
    @NotNull(message = "Name cannot be null")
    private String name;

    @Column(name = "image")
    private String image;

    @Column(name = "price") // Added price field to match database schema
    @NotNull(message = "Price cannot be null")
    private Double price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    public FavoriteItem() {}
    public FavoriteItem(Long itemId, String name, String image, Double price, User user) {
        this.itemId = itemId;
        this.name = name;
        this.image = image;
        this.price = price;
        this.user = user;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    @Override
    public String toString() {
        return "FavoriteItem{" +
                "id=" + id +
                ", itemId=" + itemId +
                ", name='" + name + '\'' +
                ", image='" + image + '\'' +
                ", price=" + price +
                '}';
    }
}