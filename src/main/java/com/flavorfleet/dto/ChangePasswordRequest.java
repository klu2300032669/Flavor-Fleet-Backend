package com.flavorfleet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public class ChangePasswordRequest {
    @NotNull(message = "Current password cannot be null")
    private String currentPassword;

    @NotNull(message = "New password cannot be null")
    @Size(min = 8, message = "New password must be at least 8 characters long")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
        message = "New password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character (@$!%*?&)"
    )
    private String newPassword;

    @NotNull(message = "Confirm password cannot be null")
    private String confirmPassword;

    public ChangePasswordRequest() {}

    public ChangePasswordRequest(String currentPassword, String newPassword, String confirmPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}