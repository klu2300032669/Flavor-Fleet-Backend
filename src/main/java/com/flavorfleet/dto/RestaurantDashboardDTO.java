package com.flavorfleet.dto;

public class RestaurantDashboardDTO {
    private Long id;
    private String name;
    private String cuisineType;
    private String address;
    private String city;
    private String phone;
    private String description;
    private boolean active;
    // You can add stats later (orders, revenue, etc.)
    private int totalOrders = 0; // Placeholder - update later
    private double totalRevenue = 0.0; // Placeholder

    // Constructors
    public RestaurantDashboardDTO() {}

    public RestaurantDashboardDTO(Long id, String name, String cuisineType, String address, String city,
                                  String phone, String description, boolean active) {
        this.id = id;
        this.name = name;
        this.cuisineType = cuisineType;
        this.address = address;
        this.city = city;
        this.phone = phone;
        this.description = description;
        this.active = active;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCuisineType() { return cuisineType; }
    public void setCuisineType(String cuisineType) { this.cuisineType = cuisineType; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getTotalOrders() { return totalOrders; }
    public void setTotalOrders(int totalOrders) { this.totalOrders = totalOrders; }
    public double getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(double totalRevenue) { this.totalRevenue = totalRevenue; }
}