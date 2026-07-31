package com.example.payandscan.payandscan.model;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class Payment {
    private String id;
    private BigDecimal amount;
    private PaymentStatus status;
    private String userEmail;
}
