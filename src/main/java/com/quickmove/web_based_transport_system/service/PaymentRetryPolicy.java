package com.quickmove.web_based_transport_system.service;

import org.springframework.stereotype.Component;

@Component
public class PaymentRetryPolicy {
    public int maxAttempts() { return 3; }
}
