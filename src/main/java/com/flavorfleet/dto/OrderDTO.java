package com.flavorfleet.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class OrderDTO {
    private Long id;
    private String userEmail;
    private String userName; // Added userName field
    private Double totalPrice;
    private String status;
    private LocalDateTime createdAt;
    private List<CartItemDTO> items = new ArrayList<>(); // Initialize to avoid null
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String pincode;

    public OrderDTO() {
    }

    public OrderDTO(Long id, String userEmail, String userName, Double totalPrice, String status, LocalDateTime createdAt,
                    List<CartItemDTO> items, String addressLine1, String addressLine2, String city, String pincode) {
        this.id = id;
        this.userEmail = userEmail;
        this.userName = userName; // Initialize userName
        this.totalPrice = totalPrice;
        this.status = status;
        this.createdAt = createdAt;
        this.items = items != null ? items : new ArrayList<>(); // Ensure items is never null
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.pincode = pincode;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(Double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<CartItemDTO> getItems() {
        return items;
    }

    public void setItems(List<CartItemDTO> items) {
        this.items = items != null ? items : new ArrayList<>(); // Ensure items is never null
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }
}