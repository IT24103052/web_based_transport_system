package com.quickmove.web_based_transport_system.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.quickmove.web_based_transport_system.entity.Ride;
import com.quickmove.web_based_transport_system.repository.RideRepository;
import java.util.List;
import java.util.Optional;

@Service
public class RideService {

    @Autowired
    private RideRepository rideRepository;

    private static final String GEOAPIFY_API_KEY = "YOUR_GEOAPIFY_API_KEY";

    public Ride createRide(Ride ride, String currentLocation) {
        if (ride == null || currentLocation == null || ride.getDropoffLocation() == null ||
                ride.getVehicleType() == null || ride.getUserId() == null) {
            throw new IllegalArgumentException("All ride details are required");
        }
        ride.setPickupLocation(currentLocation); // Use live location
        ride.setStatus("PENDING");
        double distance = calculateDistance(currentLocation, ride.getDropoffLocation());
        ride.setFare(calculateFare(distance, ride.getVehicleType()));
        return rideRepository.save(ride);
    }

    public List<String> getAvailableDrivers(String pickupLocation) {
        return List.of("Driver1", "Driver2"); // Simulate driver list
    }

    public Ride updateRideLocation(Long id, String pickupLocation, String dropoffLocation) {
        Optional<Ride> rideOpt = rideRepository.findById(id);
        if (rideOpt.isPresent() && pickupLocation != null && dropoffLocation != null) {
            Ride ride = rideOpt.get();
            ride.setPickupLocation(pickupLocation);
            ride.setDropoffLocation(dropoffLocation);
            double distance = calculateDistance(pickupLocation, dropoffLocation);
            ride.setFare(calculateFare(distance, ride.getVehicleType()));
            ride.setStatus("PENDING");
            return rideRepository.save(ride);
        }
        return null;
    }

    public boolean cancelRide(Long id) {
        Optional<Ride> rideOpt = rideRepository.findById(id);
        if (rideOpt.isPresent()) {
            Ride ride = rideOpt.get();
            ride.setStatus("CANCELLED");
            rideRepository.save(ride);
            return true;
        }
        return false;
    }

    public Ride getRideById(Long id) {
        return rideRepository.findById(id).orElse(null);
    }

    public List<Ride> getAllRides() {
        return rideRepository.findAll();
    }

    private double calculateDistance(String pickup, String dropoff) {
        return 5.0; // Default distance (replace with GeoAPI if needed)
    }

    private double calculateFare(double distance, String vehicleType) {
        switch (vehicleType.toLowerCase()) {
            case "bike": return distance * 0.5;
            case "car": return distance * 1.0;
            case "auto": return distance * 0.7;
            default: return distance * 1.0;
        }
    }
}