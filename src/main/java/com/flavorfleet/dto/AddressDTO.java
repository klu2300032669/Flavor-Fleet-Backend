package com.flavorfleet.dto;

import jakarta.validation.constraints.NotNull;

public class AddressDTO {
    private Long id;

    @NotNull(message = "Address line 1 cannot be null")
    private String line1;

    private String line2;

    @NotNull(message = "City cannot be null")
    private String city;

    @NotNull(message = "Pincode cannot be null")
    private String pincode;

    public AddressDTO() {}

    public AddressDTO(Long id, String line1, String line2, String city, String pincode) {
        this.id = id;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.pincode = pincode;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLine1() { return line1; }
    public void setLine1(String line1) { this.line1 = line1; }
    public String getLine2() { return line2; }
    public void setLine2(String line2) { this.line2 = line2; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
}