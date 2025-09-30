package com.quickmove.web_based_transport_system.service.gateway;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FakeGatewayClient implements PaymentGatewayClient {

    @Override
    public ChargeResult authorizeAndCapture(String customerToken, BigDecimal amount, String currency) {
        if (customerToken != null && customerToken.startsWith("sca_")) {
            return new ChargeResult(ResultType.REQUIRES_SCA, null, "SCA_REQUIRED");
        }
        if (customerToken != null && customerToken.startsWith("fail_")) {
            return new ChargeResult(ResultType.FAILURE, null, "INSUFFICIENT_FUNDS");
        }
        String txnId = "txn_" + System.currentTimeMillis();
        return new ChargeResult(ResultType.SUCCESS, txnId, null);
    }
}
