package com.flavorfleet.repository;

import com.flavorfleet.entity.FavoriteItem;
import com.flavorfleet.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FavoriteItemRepository extends JpaRepository<FavoriteItem, Long> {
    List<FavoriteItem> findByUser(User user);
    boolean existsByUserAndItemId(User user, Long itemId);

    @Transactional
    void deleteByUserId(Long userId);
}