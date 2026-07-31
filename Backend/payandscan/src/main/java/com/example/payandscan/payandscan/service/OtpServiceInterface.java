package com.example.payandscan.payandscan.service;

public interface OtpServiceInterface {
    String generateOtp();

    void saveOtpToRedis(String key, String otp);

    boolean validateOtpInRedis(String key, String otp);

    void sendMail(String to, String subject, String body);
}
