package com.flavorfleet.repository;

import com.flavorfleet.entity.CartItem;
import com.flavorfleet.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUser(User user);
    List<CartItem> findByUserAndOrderIsNull(User user);

    @Query("SELECT c FROM CartItem c WHERE c.user = :user AND c.itemId = :itemId AND c.order IS NULL")
    CartItem findByUserAndItemIdAndOrderIsNull(User user, Long itemId);

    @Modifying
    @Query("DELETE FROM CartItem c WHERE c.user = :user AND c.id = :id")
    void deleteByUserAndId(User user, Long id);

    @Transactional
    void deleteByUserId(Long userId);
}