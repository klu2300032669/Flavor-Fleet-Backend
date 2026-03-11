package com.flavorfleet.repository;

import com.flavorfleet.entity.PartnerApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartnerApplicationRepository extends JpaRepository<PartnerApplication, Long> {
    List<PartnerApplication> findByStatus(String status);
}