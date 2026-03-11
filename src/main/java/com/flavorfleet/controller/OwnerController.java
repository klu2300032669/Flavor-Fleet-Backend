package com.flavorfleet.controller;

import com.flavorfleet.dto.RestaurantDashboardDTO;
import com.flavorfleet.entity.User;
import com.flavorfleet.service.RestaurantService;
import com.flavorfleet.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner")
public class OwnerController {

    private final RestaurantService restaurantService;
    private final UserService userService;

    public OwnerController(RestaurantService restaurantService, UserService userService) {
        this.restaurantService = restaurantService;
        this.userService = userService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<RestaurantDashboardDTO> getDashboard(Authentication authentication) {
        String email = authentication.getName();
        User owner = userService.findByEmail(email);
        if (owner == null) {
            return ResponseEntity.notFound().build();
        }
        RestaurantDashboardDTO dto = restaurantService.getDashboardData(owner);
        return ResponseEntity.ok(dto);
    }
}