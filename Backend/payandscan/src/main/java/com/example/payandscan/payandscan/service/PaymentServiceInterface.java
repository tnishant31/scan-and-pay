package com.example.payandscan.payandscan.service;

import java.math.BigDecimal;

import com.example.payandscan.payandscan.model.Payment;
import com.example.payandscan.payandscan.model.PaymentStatus;

public interface PaymentServiceInterface {
    public Payment createPayment(BigDecimal amount, String userEmail);

    public String generateQrBase64(String paymentId) throws Exception;

    public void markPaymentAsCompleted(String paymentId);

    public PaymentStatus getPaymentStatus(String paymentId);

    public void updatePaymentStatus(String paymentId, PaymentStatus scannedProcessing);

    Payment getPayment(String paymentId);
}
