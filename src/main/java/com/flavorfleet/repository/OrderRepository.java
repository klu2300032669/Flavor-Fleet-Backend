package com.flavorfleet.repository;

import com.flavorfleet.entity.Order;
import com.flavorfleet.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUser(User user);

    @Transactional
    void deleteByUserId(Long userId);
}