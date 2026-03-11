package com.flavorfleet.repository;

import com.flavorfleet.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByEmail(String email);
    
    // NEW: Find all users by email (to handle duplicates)
    List<User> findAllByEmail(String email);

    // Find all users by role (for admin role filter)
    List<User> findByRole(String role);

    // Count total users (for dashboard)
    @Query("SELECT COUNT(u) FROM User u")
    long countAllUsers();

    // Count users by role (for dashboard stats)
    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role")
    long countByRole(@Param("role") String role);

    // Count active users (logged in within last 30 days)
    @Query("SELECT COUNT(u) FROM User u WHERE u.lastLogin IS NOT NULL AND u.lastLogin > :thirtyDaysAgo")
    long countActiveUsers(@Param("thirtyDaysAgo") LocalDateTime thirtyDaysAgo);

    // Find active users (for user management table filter)
    @Query("SELECT u FROM User u WHERE u.lastLogin IS NOT NULL AND u.lastLogin > :thirtyDaysAgo")
    List<User> findActiveUsers(@Param("thirtyDaysAgo") LocalDateTime thirtyDaysAgo);

    // Find inactive users (never logged in or >30 days)
    @Query("SELECT u FROM User u WHERE u.lastLogin IS NULL OR u.lastLogin <= :thirtyDaysAgo")
    List<User> findInactiveUsers(@Param("thirtyDaysAgo") LocalDateTime thirtyDaysAgo);

    // Find users by role and active status (for combined filters)
    @Query("SELECT u FROM User u WHERE u.role = :role AND " +
           "(u.lastLogin IS NOT NULL AND u.lastLogin > :thirtyDaysAgo)")
    List<User> findActiveUsersByRole(@Param("role") String role, @Param("thirtyDaysAgo") LocalDateTime thirtyDaysAgo);

    // NEW: Count new users created after a date (for dashboard time filters)
    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt > :startDate")
    long countByCreatedAtAfter(@Param("startDate") LocalDateTime startDate);
    
    // NEW: Find inactive users by role
    @Query("SELECT u FROM User u WHERE u.role = :role AND " +
           "(u.lastLogin IS NULL OR u.lastLogin <= :threshold)")
    List<User> findInactiveUsersByRole(@Param("role") String role, @Param("threshold") LocalDateTime threshold);
}