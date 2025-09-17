// dto/NotificationPreferencesDTO.java (no changes)
package com.flavorfleet.dto;

public class NotificationPreferencesDTO {

    private boolean emailOrderUpdates;
    private boolean emailPromotions;
    private boolean desktopNotifications;

    public boolean isEmailOrderUpdates() {
        return emailOrderUpdates;
    }

    public void setEmailOrderUpdates(boolean emailOrderUpdates) {
        this.emailOrderUpdates = emailOrderUpdates;
    }

    public boolean isEmailPromotions() {
        return emailPromotions;
    }

    public void setEmailPromotions(boolean emailPromotions) {
        this.emailPromotions = emailPromotions;
    }

    public boolean isDesktopNotifications() {
        return desktopNotifications;
    }

    public void setDesktopNotifications(boolean desktopNotifications) {
        this.desktopNotifications = desktopNotifications;
    }
}