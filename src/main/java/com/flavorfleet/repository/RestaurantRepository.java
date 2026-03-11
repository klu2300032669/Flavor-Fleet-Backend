package com.flavorfleet.repository;

import com.flavorfleet.entity.Restaurant;
import com.flavorfleet.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    Optional<Restaurant> findByOwner(User owner);

    // Optional helper
    Optional<Restaurant> findByNameIgnoreCase(String name);
}