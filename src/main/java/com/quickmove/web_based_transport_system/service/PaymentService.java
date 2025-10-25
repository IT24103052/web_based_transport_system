package com.quickmove.web_based_transport_system.service;

import com.quickmove.web_based_transport_system.entity.Payment;
import com.quickmove.web_based_transport_system.exception.ResourceNotFoundException;
import com.quickmove.web_based_transport_system.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PaymentService {
    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public Payment savePayment(Payment payment) {
        validatePayment(payment);
        Payment savedPayment = paymentRepository.save(payment);
        notifyPaymentUpdate(savedPayment);
        return savedPayment;
    }

    public Payment updatePaymentStatus(Long id, String status) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (!status.matches("PENDING|SUCCESSFUL|FAILED|REJECTED")) {
            throw new IllegalArgumentException("Status must be PENDING, SUCCESSFUL, FAILED, or REJECTED");
        }
        payment.setStatus(status);
        Payment updatedPayment = paymentRepository.save(payment);
        notifyPaymentUpdate(updatedPayment);
        return updatedPayment;
    }

    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public void deletePayment(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        paymentRepository.delete(payment);
    }

    public Payment confirmPayment(Long id, Payment confirmationDetails) {
        logger.info("confirmPayment called with id={}, incoming details: method={}, rideId={}, userId={}, driverId={}", id,
                confirmationDetails.getMethod(), confirmationDetails.getRideId(), confirmationDetails.getUserId(), confirmationDetails.getDriverId());

        // id may be either a Payment id or a rideId (client currently sends rideId)
        Optional<Payment> byId = paymentRepository.findById(id);
        Payment payment;
        if (byId.isPresent()) {
            payment = byId.get();
            logger.info("Found existing payment by payment id={}", id);
        } else {
            Optional<Payment> byRide = paymentRepository.findByRideId(id);
            if (byRide.isPresent()) {
                payment = byRide.get();
                logger.info("Found existing payment by rideId={}", id);
            } else {
                // create a new Payment record linked to this rideId
                payment = new Payment();
                payment.setRideId(id);
                payment.setStatus("PENDING"); // default for newly created manual payments
                logger.info("No existing payment found for id={}, creating new payment with rideId={}", id, id);
            }
        }

        if (!"PENDING".equals(payment.getStatus())) {
            logger.warn("Cannot confirm payment id={} because status is {}", payment.getId(), payment.getStatus());
            throw new IllegalArgumentException("Payment must be in PENDING status to confirm");
        }
        payment.setMethod(confirmationDetails.getMethod());
        payment.setCardNumber(confirmationDetails.getCardNumber());
        payment.setCvc(confirmationDetails.getCvc());
        // ensure rideId from confirmationDetails overrides if provided
        if (confirmationDetails.getRideId() != null) {
            payment.setRideId(confirmationDetails.getRideId());
        }
        payment.setUserId(confirmationDetails.getUserId());
        payment.setDriverId(confirmationDetails.getDriverId());
        payment.setVehicleType(confirmationDetails.getVehicleType());
        payment.setAmount(confirmationDetails.getAmount());
        validatePayment(payment);
        if ("CARD".equals(payment.getMethod())) {
            payment.setStatus("SUCCESSFUL"); // Simulate card success
        } else {
            payment.setStatus("PENDING"); // Manual stays PENDING until driver accepts
        }
        Payment confirmedPayment = paymentRepository.save(payment);
        logger.info("Payment confirmed/saved: id={}, rideId={}, status={}", confirmedPayment.getId(), confirmedPayment.getRideId(), confirmedPayment.getStatus());
        notifyPaymentUpdate(confirmedPayment);
        return confirmedPayment;
    }

    private void validatePayment(Payment payment) {
        if (payment.getRideId() == null || payment.getRideId() <= 0) {
            throw new IllegalArgumentException("Ride ID must be provided and valid");
        }
        if (payment.getUserId() == null || payment.getUserId().isEmpty()) {
            throw new IllegalArgumentException("User ID must be provided");
        }
        if (payment.getDriverId() == null || payment.getDriverId().isEmpty()) {
            throw new IllegalArgumentException("Driver ID must be provided");
        }
        if (payment.getAmount() == null || payment.getAmount() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (payment.getMethod() == null || !payment.getMethod().matches("MANUAL|CARD")) {
            throw new IllegalArgumentException("Payment method must be MANUAL or CARD");
        }
        if (payment.getStatus() == null || !payment.getStatus().matches("PENDING|SUCCESSFUL|FAILED|REJECTED")) {
            throw new IllegalArgumentException("Status must be PENDING, SUCCESSFUL, FAILED, or REJECTED");
        }
        if ("CARD".equals(payment.getMethod())) {
            if (payment.getCardNumber() == null || !payment.getCardNumber().matches("\\d{16}")) {
                throw new IllegalArgumentException("Valid 16-digit card number required for CARD payment");
            }
            if (payment.getCvc() == null || !payment.getCvc().matches("\\d{3}")) {
                throw new IllegalArgumentException("Valid 3-digit CVC required for CARD payment");
            }
        }
    }

    private void notifyPaymentUpdate(Payment payment) {
        try {
            if (messagingTemplate != null) {
                // Use rideId for topic
                if (payment.getRideId() != null) {
                    messagingTemplate.convertAndSend("/topic/paymentUpdates/" + payment.getRideId(), payment);
                }
                // Also publish to user-specific public topics so clients can subscribe without user principals
                if (payment.getUserId() != null && !payment.getUserId().isEmpty()) {
                    messagingTemplate.convertAndSend("/topic/userPayments/" + payment.getUserId(), payment);
                }
                if (payment.getDriverId() != null && !payment.getDriverId().isEmpty()) {
                    messagingTemplate.convertAndSend("/topic/userPayments/" + payment.getDriverId(), payment);
                }
                // Only send to users if IDs are present (legacy per-user destination)
                if (payment.getUserId() != null && !payment.getUserId().isEmpty()) {
                    try {
                        messagingTemplate.convertAndSendToUser(payment.getUserId(), "/queue/payments", payment);
                    } catch (Exception ignored) {
                        // ignore if mapping to a user session is not available
                    }
                }
                if (payment.getDriverId() != null && !payment.getDriverId().isEmpty()) {
                    try {
                        messagingTemplate.convertAndSendToUser(payment.getDriverId(), "/queue/payments", payment);
                    } catch (Exception ignored) {
                        // ignore if mapping to a user session is not available
                    }
                }
            } else {
                System.err.println("SimpMessagingTemplate not available, skipping notifications for payment id " + payment.getId());
            }
        } catch (Exception e) {
            // Log and swallow to avoid failing the request due to websocket issues
            System.err.println("Failed to notify websocket subscribers for payment id " + payment.getId() + ": " + e.getMessage());
        }
    }
}