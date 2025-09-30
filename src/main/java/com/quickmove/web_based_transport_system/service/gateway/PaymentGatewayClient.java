package com.quickmove.web_based_transport_system.service.gateway;

import java.math.BigDecimal;

public interface PaymentGatewayClient {

    enum ResultType { SUCCESS, REQUIRES_SCA, FAILURE }

    record ChargeResult(ResultType type, String transactionId, String failureReason) {}

    /**
     * Authorize & capture in one call for simplicity.
     * Implement with Stripe/Adyen/etc. in production.
     */
    ChargeResult authorizeAndCapture(String customerToken, BigDecimal amount, String currency);
}
