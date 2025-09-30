package com.quickmove.web_based_transport_system.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class EarningsService {

    public void addDriverEarning(Long rideId, Long driverId, BigDecimal gross, BigDecimal platformFee) {
        // TODO: persist payout ledger; net = gross - platformFee
    }
}
