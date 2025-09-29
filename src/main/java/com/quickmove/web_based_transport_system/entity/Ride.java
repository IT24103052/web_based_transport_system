package com.quickmove.web_based_transport_system.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "rides")
@Data
public class Ride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String userId; // Reference to User (simplified, can be enhanced with @ManyToOne)
    private String vehicleType; // bike, car, auto
    private String pickupLocation;
    private String dropoffLocation;
    private Double fare;
    private String status; // PENDING, BOOKED, CANCELLED, COMPLETED
    private String driverId; // Optional, assigned after booking
}
