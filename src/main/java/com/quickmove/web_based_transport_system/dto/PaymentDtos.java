package com.quickmove.web_based_transport_system.dto;

import com.quickmove.web_based_transport_system.entity.PaymentMethod;
import com.quickmove.web_based_transport_system.entity.PaymentStatus;

import java.math.BigDecimal;

public class PaymentDtos {

    public record PayNowRequest(
            PaymentMethod method,
            BigDecimal tipAmount,          // optional; default 0
            String customerToken,          // tokenized card / wallet id; not required for in-app wallet
            String currency                // optional; default USD
    ) {}

    public record PayNowResponse(
            Long paymentId,
            PaymentStatus status,
            String nextAction,             // e.g., "SCA_REQUIRED", "NONE"
            String transactionId,
            String receiptUrl
    ) {}
}
