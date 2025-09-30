package com.quickmove.web_based_transport_system.entity;

public enum PaymentStatus {
    PENDING,            // created, not yet sent to gateway
    AUTHORIZING,        // sending auth/capture
    REQUIRES_SCA,       // Strong Customer Authentication needed (OTP/3DS)
    CAPTURED,           // success
    FAILED              // final failure after retries
}

