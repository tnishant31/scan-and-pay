package com.example.payandscan.payandscan.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.payandscan.payandscan.model.User;
import com.example.payandscan.payandscan.repository.UserRepository;
import com.example.payandscan.payandscan.service.OtpServiceInterface;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j2;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@Log4j2
public class AuthController {
    private final UserRepository userRepository;
    private final OtpServiceInterface otpService;

    public AuthController(UserRepository userRepository, OtpServiceInterface otpService) {
        this.userRepository = userRepository;
        this.otpService = otpService;
    }

    @PostMapping("/send/sign-up/otp")
    public ResponseEntity<Map<String, String>> sendSignupOtp(HttpServletRequest request, @RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email != null) {
            email = email.trim().toLowerCase(); // Normalize email
        }
        
        String otp = otpService.generateOtp();
        otpService.saveOtpToRedis("REG_OTP:" + email, otp);
        otpService.sendMail(email, "Sign Up OTP - PayAndScan", "Your registration OTP is: " + otp);

        return ResponseEntity.ok(Map.of("message", "OTP sent successfully to email"));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(HttpServletRequest request, @RequestBody Map<String, String> requestBody) {
        log.info("Called {}, {}", request.getRequestURI(), requestBody);

        String name = requestBody.get("name");
        String email = requestBody.get("email");
        String otp = requestBody.get("otp");

        // Clean whitespace & handle lowercasing
        if (email != null) email = email.trim().toLowerCase();
        if (otp != null) otp = otp.trim();

        boolean isValid = otpService.validateOtpInRedis("REG_OTP:" + email, otp);

        if (!isValid) {
            log.warn("Invalid OTP for user: {} with OTP: {}", email, otp);
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid or expired OTP"));
        }

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setVerified(true);
        userRepository.save(user);

        log.info("Completed registration for user: {}", email);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/signin")
    public ResponseEntity<?> signIn(HttpServletRequest request, @RequestBody Map<String, String> requestBody) {
        log.info("Called {}, {}", request.getRequestURI().toString(), requestBody);

        String email = requestBody.get("email");

        if (email == null || email.trim().isEmpty()) {
            log.warn("Email is required for sign-in");
            log.error("Completed {}, {}", request.getRequestURI().toString(), requestBody);

            return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
        }

        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent()) {
            log.info("Completed {}, {}", request.getRequestURI().toString(), requestBody);

            return ResponseEntity.ok(userOpt.get());
        } else {
            log.warn("User not found: {}", email);
            log.error("Completed {}, {}", request.getRequestURI().toString(), requestBody);

            return ResponseEntity.status(404).body(Map.of("message", "User not found. Please Sign Up first."));
        }
    }

    @PostMapping("/google-register")
    public ResponseEntity<User> registerGoogleUser(HttpServletRequest request, @RequestBody Map<String, String> requestBody) {
        log.info("Called {}, {}", request.getRequestURI().toString(), requestBody);

        String name = requestBody.get("name");
        String email = requestBody.get("email");

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = new User();

            newUser.setName(name != null ? name : "Google User");
            newUser.setEmail(email);
            newUser.setVerified(true);

            log.info("Creating new user: {}", newUser);
            return userRepository.save(newUser);
        });

        log.info("Completed {}, {}", request.getRequestURI().toString(), requestBody);

        return ResponseEntity.ok(user);
    }
    
}
