package com.quickmove.web_based_transport_system.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class WalletService {

    public boolean hasSufficientBalance(Long customerId, BigDecimal amount) {
        // TODO: hook to real wallet; for now always true
        return true;
    }

    public void debit(Long customerId, BigDecimal amount) {
        // TODO: persist wallet debit
    }
}
