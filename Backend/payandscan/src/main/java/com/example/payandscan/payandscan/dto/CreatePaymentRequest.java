package com.example.payandscan.payandscan.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class CreatePaymentRequest {
    private BigDecimal amount;
    private String userEmail;
}
