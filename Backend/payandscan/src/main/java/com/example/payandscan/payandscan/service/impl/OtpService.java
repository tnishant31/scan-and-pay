package com.example.payandscan.payandscan.service.impl;

import java.security.SecureRandom;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.example.payandscan.payandscan.service.OtpServiceInterface;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class OtpService implements OtpServiceInterface {

    @Value("${spring.mail.username}")
    private String mailUsername;

    private final StringRedisTemplate stringRedisTemplate;
    private final JavaMailSender javaMailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final Duration OTP_EXPIRATION = Duration.ofMinutes(3); // 3 minutes in seconds

    public OtpService(StringRedisTemplate stringRedisTemplate, JavaMailSender javaMailSender) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.javaMailSender = javaMailSender;
    }

    @Override
    public String generateOtp() {
        String otp = String.format("%06d", secureRandom.nextInt(1000000));

        log.info("Generated OTP: {}", otp);

        return otp;
    }

    @Override
    public void saveOtpToRedis(String key, String otp) {
       log.info("Saving OTP to Redis under key: {} with TTL: {}", key, OTP_EXPIRATION);
        
        // Non-deprecated method using Duration
        stringRedisTemplate.opsForValue().set(key, otp, OTP_EXPIRATION);
    }

    @Override
    public boolean validateOtpInRedis(String key, String otp) {
        String storedOtp = stringRedisTemplate.opsForValue().get(key);

        if (storedOtp != null && storedOtp.equals(otp)) {
            log.info("OTP validation successful for key: {}", key);
            stringRedisTemplate.delete(key);
            return true;
        }

        log.warn("OTP validation failed for key: {}", key);
        return false;
    }

    @Override
    public void sendMail(String to, String subject, String body) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();

        mailMessage.setTo(to);
        mailMessage.setSubject(subject);
        mailMessage.setText(body);
        mailMessage.setFrom(mailUsername);

        javaMailSender.send(mailMessage);
    }
    
}
