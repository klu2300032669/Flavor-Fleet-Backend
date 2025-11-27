package com.flavorfleet.repository;

import com.flavorfleet.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByCategoryName(String categoryName);

    // ADDED: Flexible query for type + price
    @Query("SELECT m FROM MenuItem m WHERE (:type IS NULL OR m.type = :type) " +
           "AND (:minPrice IS NULL OR m.price > :minPrice) " +
           "AND (:maxPrice IS NULL OR m.price < :maxPrice)")
    List<MenuItem> findByTypeAndPrice(@Param("type") String type, 
                                      @Param("minPrice") Double minPrice, 
                                      @Param("maxPrice") Double maxPrice);
}