package com.example.payandscan.payandscan.service.impl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.payandscan.payandscan.model.Payment;
import com.example.payandscan.payandscan.model.PaymentStatus;
import com.example.payandscan.payandscan.service.PaymentServiceInterface;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class PaymentService implements PaymentServiceInterface {

    @Value("${local.qr.url}")
    private String qrUrl;

    private final Map<String, Payment> paymentMap = new ConcurrentHashMap<>();

    @Override
    public Payment createPayment(BigDecimal amount, String userEmail) {
        String paymentId = UUID.randomUUID().toString() + '_' + LocalDateTime.now().toString();

        log.debug("Payment ID: {}", paymentId);

        Payment payment = new Payment();

        payment.setId(paymentId);
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setUserEmail(userEmail); // Set user email

        log.info("Updating payment map");

        paymentMap.put(paymentId, payment);

        return payment;
    }

    @Override
    public String generateQrBase64(String paymentId) throws Exception {
        String qrText = qrUrl + paymentId;

        log.info("Generating QR code for payment ID: {}", paymentId);

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(qrText, BarcodeFormat.QR_CODE, 300, 300);

        BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", outputStream);
        
        byte[] qrCodeBytes = outputStream.toByteArray();

        log.info("QR code generated for payment ID: {}", paymentId);

        return Base64.getEncoder().encodeToString(qrCodeBytes);
    }

    @Override
    public void markPaymentAsCompleted(String paymentId) {
        Payment payment = paymentMap.get(paymentId);

        if (payment != null) {
            log.info("Marking payment ID {} as completed", paymentId);
            payment.setStatus(PaymentStatus.COMPLETED);
        }
    }

    @Override
    public PaymentStatus getPaymentStatus(String paymentId) {
        Payment payment = paymentMap.get(paymentId);

        return payment != null ? payment.getStatus() : null;
    }

    @Override
    public void updatePaymentStatus(String paymentId, PaymentStatus status) {
        Payment payment = paymentMap.get(paymentId);

        if (payment != null) {
            payment.setStatus(status);
        }
    }

    @Override
    public Payment getPayment(String paymentId) {
        return paymentMap.get(paymentId);
    }
}
