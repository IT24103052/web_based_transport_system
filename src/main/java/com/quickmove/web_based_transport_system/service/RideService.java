package com.quickmove.web_based_transport_system.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.quickmove.web_based_transport_system.entity.Ride;
import com.quickmove.web_based_transport_system.repository.RideRepository;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import org.json.JSONArray;
import org.json.JSONObject;

@Service
public class RideService {

    @Autowired
    private RideRepository rideRepository;

    private static final String GEOAPIFY_API_KEY = "YOUR_GEOAPIFY_API_KEY";

    public Ride createRide(Ride ride) {
        if (ride == null || ride.getPickupLocation() == null || ride.getDropoffLocation() == null || ride.getVehicleType() == null || ride.getFare() == null) {
            throw new IllegalArgumentException("Ride details cannot be null");
        }
        ride.setStatus("PENDING");
        return rideRepository.save(ride);
    }

    public Ride updateRideLocation(Long id, String pickupLocation, String dropoffLocation) {
        Optional<Ride> rideOpt = rideRepository.findById(id);
        if (rideOpt.isPresent()) {
            Ride ride = rideOpt.get();
            ride.setPickupLocation(pickupLocation);
            ride.setDropoffLocation(dropoffLocation);
            double distance = calculateDistance(pickupLocation, dropoffLocation);
            ride.setFare(calculateFare(distance, ride.getVehicleType()));
            ride.setStatus("PENDING"); // Reset status if updated
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
        try {
            String urlStr = String.format("https://api.geoapify.com/v1/routing?waypoints=%s|%s&mode=drive&apiKey=%s",
                    pickup.replace(" ", "+"), dropoff.replace(" ", "+"), GEOAPIFY_API_KEY);
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            JSONObject json = new JSONObject(response.toString());
            JSONArray features = json.getJSONArray("features");
            if (features.length() > 0) {
                JSONObject properties = features.getJSONObject(0).getJSONObject("properties");
                return properties.getDouble("distance") / 1000.0; // Convert to km
            }
            return 5.0; // Fallback
        } catch (Exception e) {
            e.printStackTrace();
            return 5.0; // Fallback
        }
    }

    private double calculateFare(double distance, String vehicleType) {
        switch (vehicleType.toLowerCase()) {
            case "bike":
                return distance * 0.5;
            case "car":
                return distance * 1.0;
            case "auto":
                return distance * 0.7;
            default:
                return distance * 1.0;
        }
    }
}
