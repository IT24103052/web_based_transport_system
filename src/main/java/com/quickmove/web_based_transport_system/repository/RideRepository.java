package com.quickmove.web_based_transport_system.repository;

import com.quickmove.web_based_transport_system.entity.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RideRepository extends JpaRepository<Ride, Long> {
    List<Ride> findByUserId(String userId);
    List<Ride> findByStatus(String status);
}