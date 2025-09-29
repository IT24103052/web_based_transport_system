package com.quickmove.web_based_transport_system.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.quickmove.web_based_transport_system.entity.Ride;
import com.quickmove.web_based_transport_system.service.RideService;
import java.util.List;

@RestController
@RequestMapping("/api/rides")
public class RideController {
    @Autowired
    private RideService rideService;

    @PostMapping("/book")
    public ResponseEntity<Ride> bookRide(@RequestBody Ride ride) {
        return ResponseEntity.ok(rideService.createRide(ride));
    }

    @PutMapping("/update-location/{id}")
    public ResponseEntity<Ride> updateLocation(@PathVariable Long id, @RequestBody Ride ride) {
        Ride updated = rideService.updateRideLocation(id, ride.getPickupLocation(), ride.getDropoffLocation());
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/cancel/{id}")
    public ResponseEntity<String> cancelRide(@PathVariable Long id) {
        return rideService.cancelRide(id) ? ResponseEntity.ok("Ride cancelled") : ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Ride> getRide(@PathVariable Long id) {
        Ride ride = rideService.getRideById(id);
        return ride != null ? ResponseEntity.ok(ride) : ResponseEntity.notFound().build();
    }

    @GetMapping
    public ResponseEntity<List<Ride>> getAllRides() {
        return ResponseEntity.ok(rideService.getAllRides());
    }
}
