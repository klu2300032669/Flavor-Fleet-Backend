package com.flavorfleet.service;

import com.flavorfleet.dto.RestaurantDashboardDTO;
import com.flavorfleet.entity.PartnerApplication;
import com.flavorfleet.entity.Restaurant;
import com.flavorfleet.entity.User;
import com.flavorfleet.repository.RestaurantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;

    public RestaurantService(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    @Transactional
    public Restaurant createFromApplication(PartnerApplication app, User owner) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(app.getRestaurantName());
        restaurant.setOwner(owner);
        restaurant.setCuisineType(app.getCuisineType());
        restaurant.setAddress(app.getAddress());
        restaurant.setCity(app.getCity());
        restaurant.setPhone(app.getPhone());
        restaurant.setDescription(app.getMessage());
        restaurant.setActive(true);
        return restaurantRepository.save(restaurant);
    }

    @Transactional(readOnly = true)
    public RestaurantDashboardDTO getDashboardData(User owner) {
        Restaurant restaurant = restaurantRepository.findByOwner(owner)
                .orElseThrow(() -> new IllegalArgumentException("No restaurant found for this owner"));

        RestaurantDashboardDTO dto = new RestaurantDashboardDTO();
        dto.setId(restaurant.getId());
        dto.setName(restaurant.getName());
        dto.setCuisineType(restaurant.getCuisineType());
        dto.setAddress(restaurant.getAddress());
        dto.setCity(restaurant.getCity());
        dto.setPhone(restaurant.getPhone());
        dto.setDescription(restaurant.getDescription());
        dto.setActive(restaurant.isActive());

        // TODO: Later add real stats from OrderService
        // Example placeholder:
        dto.setTotalOrders(42);
        dto.setTotalRevenue(12500.75);

        return dto;
    }
}