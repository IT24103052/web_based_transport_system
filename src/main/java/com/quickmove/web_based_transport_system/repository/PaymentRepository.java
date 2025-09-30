package com.quickmove.web_based_transport_system.repository;

import com.quickmove.web_based_transport_system.entity.Payment;
import com.quickmove.web_based_transport_system.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findTopByRideIdOrderByIdDesc(Long rideId);
    List<Payment> findByStatus(PaymentStatus status);
}
