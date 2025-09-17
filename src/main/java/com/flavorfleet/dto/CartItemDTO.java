package com.flavorfleet.dto;

public class CartItemDTO {

    private Long id;
    private Long itemId;
    private String name;
    private Double price;
    private Integer quantity;
    private String image;

    public CartItemDTO() {
    }

    public CartItemDTO(Long id, Long itemId, String name, Double price, Integer quantity, String image) {
        this.id = id;
        this.itemId = itemId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.image = image;
    }

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
}