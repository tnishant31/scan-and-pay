package com.example.payandscan.payandscan.dto;

import java.math.BigDecimal;

import com.example.payandscan.payandscan.model.PaymentStatus;

import lombok.Data;

@Data
public class CreatePaymentResponse {
    private String id;
    private BigDecimal amount;
    private PaymentStatus status;
    private String qrCodeBase64;
}
