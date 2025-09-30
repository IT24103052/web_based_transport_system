package com.quickmove.web_based_transport_system.service;

import com.quickmove.web_based_transport_system.entity.Payment;
import org.springframework.stereotype.Service;

@Service
public class ReceiptService {

    public String generateAndDeliver(Payment payment, String customerEmailOrPhone) {
        // TODO: generate PDF / HTML and email/SMS. Return accessible URL.
        return "https://receipts.quickmove/local/" + payment.getId();
    }
}
