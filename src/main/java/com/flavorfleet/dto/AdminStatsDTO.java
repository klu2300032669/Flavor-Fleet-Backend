package com.flavorfleet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class AdminStatsDTO {
    
    // User Stats
    private long totalUsers;
    private long activeUsers;
    private long inactiveUsers;
    private long adminUsers;
    private long newUsers;  // For selected time period

    // Order Stats
    private long totalOrders;
    private BigDecimal totalRevenue;
    private BigDecimal avgOrderValue;
    private Map<String, Long> orderStatusCount;  // e.g., {"Pending": 5, "Delivered": 10}

    // Menu Stats
    private long totalMenuItems;
    private long totalCategories;

    // Time Filter
    private String timeRange;  // "7d", "30d", "all"
    private LocalDateTime timeStart;  // Start date for filter
    private LocalDateTime timeEnd;    // End date for filter

    // Trends (mocked for now - you can compute real trends later)
    private double revenueTrendPercent;

    // Constructor
    public AdminStatsDTO() {
        // Default constructor
    }

    public AdminStatsDTO(long totalUsers, long activeUsers, long inactiveUsers, long adminUsers, long newUsers,
                         long totalOrders, BigDecimal totalRevenue, BigDecimal avgOrderValue,
                         Map<String, Long> orderStatusCount, long totalMenuItems, long totalCategories,
                         String timeRange, LocalDateTime timeStart, LocalDateTime timeEnd, double revenueTrendPercent) {
        this.totalUsers = totalUsers;
        this.activeUsers = activeUsers;
        this.inactiveUsers = inactiveUsers;
        this.adminUsers = adminUsers;
        this.newUsers = newUsers;
        this.totalOrders = totalOrders;
        this.totalRevenue = totalRevenue;
        this.avgOrderValue = avgOrderValue;
        this.orderStatusCount = orderStatusCount;
        this.totalMenuItems = totalMenuItems;
        this.totalCategories = totalCategories;
        this.timeRange = timeRange;
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
        this.revenueTrendPercent = revenueTrendPercent;
    }

    // Getters and Setters
    public long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public long getActiveUsers() {
        return activeUsers;
    }

    public void setActiveUsers(long activeUsers) {
        this.activeUsers = activeUsers;
    }

    public long getInactiveUsers() {
        return inactiveUsers;
    }

    public void setInactiveUsers(long inactiveUsers) {
        this.inactiveUsers = inactiveUsers;
    }

    public long getAdminUsers() {
        return adminUsers;
    }

    public void setAdminUsers(long adminUsers) {
        this.adminUsers = adminUsers;
    }

    public long getNewUsers() {
        return newUsers;
    }

    public void setNewUsers(long newUsers) {
        this.newUsers = newUsers;
    }

    public long getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(long totalOrders) {
        this.totalOrders = totalOrders;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public BigDecimal getAvgOrderValue() {
        return avgOrderValue != null ? avgOrderValue : BigDecimal.ZERO;
    }

    public void setAvgOrderValue(BigDecimal avgOrderValue) {
        this.avgOrderValue = avgOrderValue;
    }

    public Map<String, Long> getOrderStatusCount() {
        return orderStatusCount != null ? orderStatusCount : Map.of();
    }

    public void setOrderStatusCount(Map<String, Long> orderStatusCount) {
        this.orderStatusCount = orderStatusCount;
    }

    public long getTotalMenuItems() {
        return totalMenuItems;
    }

    public void setTotalMenuItems(long totalMenuItems) {
        this.totalMenuItems = totalMenuItems;
    }

    public long getTotalCategories() {
        return totalCategories;
    }

    public void setTotalCategories(long totalCategories) {
        this.totalCategories = totalCategories;
    }

    public String getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(String timeRange) {
        this.timeRange = timeRange;
    }

    public LocalDateTime getTimeStart() {
        return timeStart;
    }

    public void setTimeStart(LocalDateTime timeStart) {
        this.timeStart = timeStart;
    }

    public LocalDateTime getTimeEnd() {
        return timeEnd;
    }

    public void setTimeEnd(LocalDateTime timeEnd) {
        this.timeEnd = timeEnd;
    }

    public double getRevenueTrendPercent() {
        return revenueTrendPercent;
    }

    public void setRevenueTrendPercent(double revenueTrendPercent) {
        this.revenueTrendPercent = revenueTrendPercent;
    }

    @Override
    public String toString() {
        return "AdminStatsDTO{" +
                "totalUsers=" + totalUsers +
                ", activeUsers=" + activeUsers +
                ", inactiveUsers=" + inactiveUsers +
                ", adminUsers=" + adminUsers +
                ", newUsers=" + newUsers +
                ", totalOrders=" + totalOrders +
                ", totalRevenue=" + totalRevenue +
                ", avgOrderValue=" + avgOrderValue +
                ", orderStatusCount=" + orderStatusCount +
                ", totalMenuItems=" + totalMenuItems +
                ", totalCategories=" + totalCategories +
                ", timeRange='" + timeRange + '\'' +
                ", timeStart=" + timeStart +
                ", timeEnd=" + timeEnd +
                ", revenueTrendPercent=" + revenueTrendPercent +
                '}';
    }
}