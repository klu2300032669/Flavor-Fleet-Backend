package com.flavorfleet.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @NotNull(message = "Name cannot be null")
    @NotBlank(message = "Name cannot be blank")
    private String name;

    @Column(nullable = false, unique = true)
    @NotNull(message = "Email cannot be null")
    @Email(message = "Email should be valid")
    private String email;

    @Column(nullable = false)
    @JsonIgnore
    @NotNull(message = "Password cannot be null")
    private String password;

    @Column(nullable = false)
    @NotNull(message = "Role cannot be null")
    private String role = "ROLE_USER"; // Default to ROLE_USER

    @Column
    private String profilePicture;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Order> orders = new ArrayList<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JsonIgnore
    private List<CartItem> cartItems = new ArrayList<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JsonIgnore
    private List<FavoriteItem> favoriteItems = new ArrayList<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Address> addresses = new ArrayList<>();

    @Column(nullable = true)
    private Boolean emailOrderUpdates = false;

    @Column(nullable = true)
    private Boolean emailPromotions = false;

    @Column(nullable = true)
    private Boolean desktopNotifications = false;

    public User() {}

    public User(String name, String email, String password, String role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role != null && role.startsWith("ROLE_") ? role : "ROLE_" + (role != null ? role.toUpperCase() : "USER");
        this.emailOrderUpdates = false;
        this.emailPromotions = false;
        this.desktopNotifications = false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase(); }
    public String getProfilePicture() { return profilePicture; }
    public void setProfilePicture(String profilePicture) { this.profilePicture = profilePicture; }
    public List<Order> getOrders() { return orders; }
    public void setOrders(List<Order> orders) { 
        this.orders = orders != null ? orders : new ArrayList<>();
    }
    public List<CartItem> getCartItems() { return cartItems; }
    public void setCartItems(List<CartItem> cartItems) { 
        this.cartItems = cartItems != null ? cartItems : new ArrayList<>();
    }
    public List<FavoriteItem> getFavoriteItems() { return favoriteItems; }
    public void setFavoriteItems(List<FavoriteItem> favoriteItems) { 
        this.favoriteItems = favoriteItems != null ? favoriteItems : new ArrayList<>();
    }
    public List<Address> getAddresses() { return addresses; }
    public void setAddresses(List<Address> addresses) { 
        this.addresses = addresses != null ? addresses : new ArrayList<>();
    }
    public Boolean isEmailOrderUpdates() { return emailOrderUpdates != null ? emailOrderUpdates : false; }
    public void setEmailOrderUpdates(Boolean emailOrderUpdates) { this.emailOrderUpdates = emailOrderUpdates; }
    public Boolean isEmailPromotions() { return emailPromotions != null ? emailPromotions : false; }
    public void setEmailPromotions(Boolean emailPromotions) { this.emailPromotions = emailPromotions; }
    public Boolean isDesktopNotifications() { return desktopNotifications != null ? desktopNotifications : false; }
    public void setDesktopNotifications(Boolean desktopNotifications) { this.desktopNotifications = desktopNotifications; }

    public void addOrder(Order order) {
        if (order != null && !orders.contains(order)) {
            orders.add(order);
            order.setUser(this);
        }
    }

    public void addCartItem(CartItem item) {
        if (item != null && !cartItems.contains(item)) {
            cartItems.add(item);
            item.setUser(this);
        }
    }

    public void addFavoriteItem(FavoriteItem item) {
        if (item != null && !favoriteItems.contains(item)) {
            favoriteItems.add(item);
            item.setUser(this);
        }
    }

    public void addAddress(Address address) {
        if (address != null && !addresses.contains(address)) {
            addresses.add(address);
            address.setUser(this);
        }
    }

    public void removeAddress(Address address) {
        if (address != null) {
            addresses.remove(address);
            address.setUser(null);
        }
    }

    @JsonProperty("ordersCount")
    public int getOrdersCount() {
        return orders.size();
    }

    @JsonProperty("cartItemsCount")
    public int getCartItemsCount() {
        return cartItems.size();
    }

    @JsonProperty("favoriteItemsCount")
    public int getFavoriteItemsCount() {
        return favoriteItems.size();
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", role='" + role + '\'' +
                ", profilePicture='" + profilePicture + '\'' +
                ", ordersCount=" + getOrdersCount() +
                ", cartItemsCount=" + getCartItemsCount() +
                ", favoriteItemsCount=" + getFavoriteItemsCount() +
                ", addressesCount=" + addresses.size() +
                '}';
    }
}