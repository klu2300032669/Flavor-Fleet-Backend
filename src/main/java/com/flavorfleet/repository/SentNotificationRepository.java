// repository/SentNotificationRepository.java (no changes)
package com.flavorfleet.repository;

import com.flavorfleet.entity.SentNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SentNotificationRepository extends JpaRepository<SentNotification, Long> {
    List<SentNotification> findByStatusAndScheduleDateBefore(String status, LocalDateTime now);
    
    @Query("SELECT s FROM SentNotification s ORDER BY s.sentAt DESC")
    List<SentNotification> findAllByOrderBySentAtDesc();
    
    @Query("SELECT s FROM SentNotification s WHERE s.status = :status AND s.scheduleDate < :now")
    List<SentNotification> findPendingScheduled(@Param("status") String status, @Param("now") LocalDateTime now);
}