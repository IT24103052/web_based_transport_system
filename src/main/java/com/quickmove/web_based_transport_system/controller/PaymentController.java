package com.quickmove.web_based_transport_system.controller;

import com.quickmove.web_based_transport_system.dto.PaymentDtos.PayNowRequest;
import com.quickmove.web_based_transport_system.dto.PaymentDtos.PayNowResponse;
import com.quickmove.web_based_transport_system.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    /** Triggered when driver taps End Ride or customer taps Pay Now */
    @PostMapping("/rides/{rideId}/pay")
    public ResponseEntity<PayNowResponse> pay(@PathVariable Long rideId, @RequestBody PayNowRequest request) {
        return ResponseEntity.ok(payments.pay(rideId, request));
    }

    /** Complete SCA/OTP then resume capture */
    @PostMapping("/{paymentId}/confirm-sca")
    public ResponseEntity<PayNowResponse> confirmSca(@PathVariable Long paymentId, @RequestParam String scaToken) {
        return ResponseEntity.ok(payments.confirmSca(paymentId, scaToken));
    }
}
