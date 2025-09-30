package com.quickmove.web_based_transport_system.service;

import com.quickmove.web_based_transport_system.dto.PaymentDtos.PayNowRequest;
import com.quickmove.web_based_transport_system.dto.PaymentDtos.PayNowResponse;
import com.quickmove.web_based_transport_system.entity.Payment;
import com.quickmove.web_based_transport_system.entity.PaymentMethod;
import com.quickmove.web_based_transport_system.entity.PaymentStatus;
import com.quickmove.web_based_transport_system.repository.PaymentRepository;
import com.quickmove.web_based_transport_system.repository.RideRepository;
import com.quickmove.web_based_transport_system.service.gateway.PaymentGatewayClient;
import com.quickmove.web_based_transport_system.service.gateway.PaymentGatewayClient.ChargeResult;
import com.quickmove.web_based_transport_system.service.gateway.PaymentGatewayClient.ResultType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Objects;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepo;
    private final RideRepository rideRepo;
    private final PaymentGatewayClient gateway;
    private final WalletService walletService;
    private final EarningsService earningsService;
    private final ReceiptService receiptService;
    private final PaymentRetryPolicy retryPolicy;

    public PaymentService(PaymentRepository paymentRepo,
                          RideRepository rideRepo,
                          PaymentGatewayClient gateway,
                          WalletService walletService,
                          EarningsService earningsService,
                          ReceiptService receiptService,
                          PaymentRetryPolicy retryPolicy) {
        this.paymentRepo = paymentRepo;
        this.rideRepo = rideRepo;
        this.gateway = gateway;
        this.walletService = walletService;
        this.earningsService = earningsService;
        this.receiptService = receiptService;
        this.retryPolicy = retryPolicy;
    }

    public PayNowResponse pay(Long rideId, PayNowRequest req) {
        var ride = rideRepo.findById(rideId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));

        // Preconditions
        String status = Objects.toString(getField(ride, "status"), "");
        if (!"COMPLETED".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ride must be COMPLETED to pay");
        }

        BigDecimal fareAmount = (BigDecimal) getField(ride, "finalFare"); // expected to exist
        if (fareAmount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Final fare not available");
        }

        BigDecimal tip = req.tipAmount() != null ? req.tipAmount() : BigDecimal.ZERO;
        BigDecimal total = fareAmount.add(tip);
        String currency = req.currency() != null ? req.currency() : "USD";

        var payment = new Payment();
        payment.setRideId(rideId);
        payment.setMethod(req.method());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setFareAmount(fareAmount);
        payment.setTipAmount(tip);
        payment.setTotalAmount(total);
        payment.setCurrency(currency);
        payment.setCustomerToken(req.customerToken());
        payment = paymentRepo.save(payment);

        // Main flow & extensions
        if (req.method() == PaymentMethod.IN_APP_WALLET) {
            Long customerId = (Long) getField(ride, "customerId");
            if (!walletService.hasSufficientBalance(customerId, total)) {
                throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Insufficient wallet balance");
            }
            walletService.debit(customerId, total);
            finalizeSuccess(payment, ride, "wallet_" + System.currentTimeMillis());
            return new PayNowResponse(payment.getId(), payment.getStatus(), "NONE", payment.getTransactionId(), payment.getReceiptUrl());
        }

        // CARD / MOBILE_WALLET
        if (req.customerToken() == null || req.customerToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment token is required");
        }

        payment.setStatus(PaymentStatus.AUTHORIZING);
        paymentRepo.save(payment);

        ChargeResult result = gateway.authorizeAndCapture(req.customerToken(), total, currency);

        if (result.type() == ResultType.REQUIRES_SCA) {
            payment.setStatus(PaymentStatus.REQUIRES_SCA);
            payment.setFailureReason("SCA_REQUIRED");
            paymentRepo.save(payment);
            return new PayNowResponse(payment.getId(), payment.getStatus(), "SCA_REQUIRED", null, null);
        }

        if (result.type() == ResultType.FAILURE) {
            handleFailure(payment, result.failureReason());
            return new PayNowResponse(payment.getId(), payment.getStatus(), "RETRY_ALLOWED", null, null);
        }

        // SUCCESS
        payment.setTransactionId(result.transactionId());
        finalizeSuccess(payment, ride, result.transactionId());
        return new PayNowResponse(payment.getId(), payment.getStatus(), "NONE", payment.getTransactionId(), payment.getReceiptUrl());
    }

    public PayNowResponse confirmSca(Long paymentId, String scaToken) {
        var payment = paymentRepo.findById(paymentId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        if (payment.getStatus() != PaymentStatus.REQUIRES_SCA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment is not awaiting SCA");
        }
        // Re-attempt capture post-SCA
        var result = gateway.authorizeAndCapture(payment.getCustomerToken(), payment.getTotalAmount(), payment.getCurrency());
        if (result.type() == ResultType.SUCCESS) {
            var ride = rideRepo.findById(payment.getRideId()).orElseThrow();
            payment.setTransactionId(result.transactionId());
            finalizeSuccess(payment, ride, result.transactionId());
            return new PayNowResponse(payment.getId(), payment.getStatus(), "NONE", payment.getTransactionId(), payment.getReceiptUrl());
        }
        handleFailure(payment, result.failureReason());
        return new PayNowResponse(payment.getId(), payment.getStatus(), "RETRY_ALLOWED", null, null);
    }

    private void finalizeSuccess(Payment payment, Object ride, String txnId) {
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setTransactionId(txnId);
        payment.setFailureReason(null);
        paymentRepo.save(payment);

        // Update ride: mark paid
        setField(ride, "paymentStatus", "PAID");
        setField(ride, "paidAmount", payment.getTotalAmount());
        rideRepo.save(ride);

        // Update driver earnings (net of platform fees)
        Long driverId = (Long) getField(ride, "driverId");
        var fee = payment.getFareAmount().multiply(new java.math.BigDecimal("0.20")); // 20% platform fee (example)
        earningsService.addDriverEarning(payment.getRideId(), driverId, payment.getFareAmount().add(payment.getTipAmount()), fee);

        // Generate & deliver receipt
        String customerContact = (String) getField(ride, "customerContact");
        String receiptUrl = receiptService.generateAndDeliver(payment, customerContact);
        payment.setReceiptUrl(receiptUrl);
        paymentRepo.save(payment);
    }

    private void handleFailure(Payment payment, String reason) {
        int attempts = countAttempts(payment.getRideId());
        if (attempts >= retryPolicy.maxAttempts() - 1) {
            payment.setStatus(PaymentStatus.FAILED);
            setRidePaymentPending(payment.getRideId(), reason);
        } else {
            payment.setStatus(PaymentStatus.PENDING); // allow retry/change method
            setRidePaymentPending(payment.getRideId(), reason);
        }
        payment.setFailureReason(reason);
        paymentRepo.save(payment);
    }

    private int countAttempts(Long rideId) {
        return (int) paymentRepo.findTopByRideIdOrderByIdDesc(rideId).stream().count();
    }

    private void setRidePaymentPending(Long rideId, String reason) {
        var ride = rideRepo.findById(rideId).orElseThrow();
        setField(ride, "paymentStatus", "PENDING");
        setField(ride, "paymentFailureReason", reason);
        rideRepo.save(ride);
    }

    /* --- Reflection helpers to avoid changing your existing Ride.java API right now --- */
    private static Object getField(Object obj, String field) {
        try { var f = obj.getClass().getDeclaredField(field); f.setAccessible(true); return f.get(obj); }
        catch (Exception e) { return null; }
    }
    private static void setField(Object obj, String field, Object value) {
        try { var f = obj.getClass().getDeclaredField(field); f.setAccessible(true); f.set(obj, value); }
        catch (Exception ignored) { }
    }
}
