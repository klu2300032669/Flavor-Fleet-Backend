package com.flavorfleet.repository;

import com.flavorfleet.entity.Notification;
import com.flavorfleet.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByUserOrderBySentAtDesc(User user, Pageable pageable);
    List<Notification> findByUserAndIsReadFalse(User user);
    void deleteByUser(User user);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}