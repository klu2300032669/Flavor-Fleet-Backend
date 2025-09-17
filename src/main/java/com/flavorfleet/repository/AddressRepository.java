package com.flavorfleet.repository;

import com.flavorfleet.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface AddressRepository extends JpaRepository<Address, Long> {
    @Transactional
    void deleteByUserId(Long userId);
}