package com.example.payandscan.payandscan.controller;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.payandscan.payandscan.dto.CreatePaymentRequest;
import com.example.payandscan.payandscan.dto.CreatePaymentResponse;
import com.example.payandscan.payandscan.dto.PaymentStatusResponse;
import com.example.payandscan.payandscan.model.Payment;
import com.example.payandscan.payandscan.model.PaymentStatus;
import com.example.payandscan.payandscan.service.OtpServiceInterface;
import com.example.payandscan.payandscan.service.PaymentServiceInterface;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j2;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "http://localhost:4200")
@Log4j2
public class PaymentController {

    private final PaymentServiceInterface paymentService;
    private final OtpServiceInterface otpService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    PaymentController(PaymentServiceInterface paymentService, OtpServiceInterface otpService) {
        this.paymentService = paymentService;
        this.otpService = otpService;
    }

    @PostMapping
    public ResponseEntity<CreatePaymentResponse> createPayment(HttpServletRequest request, @RequestBody CreatePaymentRequest paymentRequest) throws Exception {
        log.info("Called {}, {}", request.getRequestURI().toString(), paymentRequest);

        Payment payment = paymentService.createPayment(paymentRequest.getAmount(), paymentRequest.getUserEmail());

        String qrBase64 = paymentService.generateQrBase64(payment.getId());

        CreatePaymentResponse response = new CreatePaymentResponse();
        response.setId(payment.getId());
        response.setQrCodeBase64(qrBase64);
        response.setAmount(payment.getAmount());
        response.setStatus(payment.getStatus());

        log.info("Completed {}, {}", request.getRequestURI().toString(), response);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(HttpServletRequest request,
        @RequestParam (required = true) String paymentId) {

            log.info("Called {}, {}", request.getRequestURI().toString(), paymentId);

            PaymentStatus status = paymentService.getPaymentStatus(paymentId);

            if (status == null) {
                log.error("Completed {}, {}", request.getRequestURI().toString(), paymentId);
                return ResponseEntity.notFound().build();
            }

            PaymentStatusResponse response = new PaymentStatusResponse();
            response.setId(paymentId);
            response.setStatus(status.name());

            log.info("Completed {}, {}", request.getRequestURI().toString(), paymentId);

            return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/scan-success/{paymentId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> scanSuccess(@PathVariable String paymentId) {

        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Confirm Payment</title>
                <style>
                    body { font-family: sans-serif; background: #f8fafc; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; padding: 20px; }
                    .card { background: white; padding: 30px; border-radius: 20px; box-shadow: 0 10px 25px rgba(0,0,0,0.1); text-align: center; max-width: 350px; width: 100%; }
                    .btn-group { display: flex; gap: 12px; margin-top: 25px; }
                    .btn { flex: 1; padding: 14px; border-radius: 12px; font-weight: 700; border: none; cursor: pointer; }
                    .btn-yes { background: #4f46e5; color: white; }
                    .btn-no { background: #f1f5f9; color: #64748b; }
                    .input-otp { width: 100%; padding: 12px; margin-top: 15px; border: 1px solid #cbd5e1; border-radius: 8px; font-size: 16px; box-sizing: border-box; text-align: center; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div id="confirm-box">
                        <h3 style="margin: 0; color: #475569; font-size: 14px;">CONFIRM PAYMENT</h3>
                        <p style="margin-top: 10px; color: #1e293b; font-size: 16px;">Are you sure you want to pay?</p>
                        
                        <div class="btn-group">
                            <button onclick="onYes()" class="btn btn-yes">Yes, Pay</button>
                            <button onclick="onNo()" class="btn btn-no">No, Cancel</button>
                        </div>
                    </div>

                    <!-- OTP Input Box on Phone -->
                    <div id="otp-box" style="display: none;">
                        <h3 style="margin:0; color:#1e293b;">Enter Email OTP</h3>
                        <p style="font-size:12px; color:#64748b;">An OTP has been sent to your registered email.</p>
                        <input id="otp-input" type="text" placeholder="6-digit OTP" class="input-otp" />
                        <button onclick="submitOtp()" class="btn btn-yes" style="width:100%; margin-top:15px;">Verify & Complete</button>
                    </div>

                    <div id="status-box" style="display: none; font-weight: bold; margin-top: 15px;"></div>
                </div>

                <script>
                    const paymentId = 'PAYMENT_ID_PLACEHOLDER';

                    function onYes() {
                        document.getElementById('confirm-box').style.display = 'none';
                        document.getElementById('otp-box').style.display = 'block';

                        // Request backend to send Payment OTP to registered user email
                        fetch('/api/payments/send-payment-otp/' + paymentId, { method: 'POST' });
                    }

                    function submitOtp() {
                        const otp = document.getElementById('otp-input').value;
                        fetch('/api/payments/verify-payment-otp/' + paymentId, {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ otp: otp })
                        }).then(res => {
                            if (res.ok) {
                                document.getElementById('otp-box').style.display = 'none';
                                const statusEl = document.getElementById('status-box');
                                statusEl.style.display = 'block';
                                statusEl.style.color = '#059669';
                                statusEl.innerText = 'Payment Successful, Thank You!';
                            } else {
                                alert('Invalid or expired OTP!');
                            }
                        });
                    }

                    function onNo() {
                        document.getElementById('confirm-box').style.display = 'none';
                        const statusEl = document.getElementById('status-box');
                        statusEl.style.display = 'block';
                        statusEl.style.color = '#e11d48';
                        statusEl.innerText = 'Payment Cancelled by User.';

                        fetch('/api/payments/cancel/' + paymentId, { method: 'POST' });
                    }
                </script>
            </body>
            </html>
            """.replace("PAYMENT_ID_PLACEHOLDER", paymentId);

        return ResponseEntity.ok(html);
    }

    @PostMapping("/confirm/{paymentId}")
    public ResponseEntity<Void> confirmPayment(@PathVariable String paymentId) {
        paymentService.markPaymentAsCompleted(paymentId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/step-bank-info/{paymentId}")
    public ResponseEntity<Void> stepBankInfo(@PathVariable String paymentId) {
        paymentService.updatePaymentStatus(paymentId, PaymentStatus.FETCHING_BANK_INFO);
        return ResponseEntity.ok().build();
    }

    // Called when user clicks "Yes, Pay" on phone
    @PostMapping("/start-processing/{paymentId}")
    public ResponseEntity<Void> startProcessing(@PathVariable String paymentId) {
        log.info("User confirmed payment on phone for ID: {}", paymentId);
        
        paymentService.updatePaymentStatus(paymentId, PaymentStatus.SCANNED_PROCESSING);

        scheduler.schedule(() -> {
            paymentService.updatePaymentStatus(paymentId, PaymentStatus.FETCHING_BANK_INFO);
        }, 3, TimeUnit.SECONDS);

        scheduler.schedule(() -> {
            paymentService.markPaymentAsCompleted(paymentId);
        }, 6, TimeUnit.SECONDS);

        return ResponseEntity.ok().build();
    }

    // Called when user clicks "No, Cancel" on phone
    @PostMapping("/cancel/{paymentId}")
    public ResponseEntity<Void> cancelPayment(@PathVariable String paymentId) {
        log.info("User cancelled payment on phone for ID: {}", paymentId);
        paymentService.updatePaymentStatus(paymentId, PaymentStatus.FAILED);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/send-payment-otp/{paymentId}")
    public ResponseEntity<Void> sendPaymentOtp(@PathVariable String paymentId) {
        String otp = otpService.generateOtp();
        
        // Save in Redis under key "PAY_OTP:<paymentId>" with 5 min TTL
        otpService.saveOtpToRedis("PAY_OTP:" + paymentId, otp);
        
        // Update status on Mac UI to processing
        paymentService.updatePaymentStatus(paymentId, PaymentStatus.SCANNED_PROCESSING);

        Payment payment = paymentService.getPayment(paymentId);
        
        if (payment != null && payment.getUserEmail() != null) {
            otpService.sendMail(payment.getUserEmail(), "Payment Confirmation OTP", "Your payment verification OTP is: " + otp);
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-payment-otp/{paymentId}")
    public ResponseEntity<?> verifyPaymentOtp(@PathVariable String paymentId, @RequestBody Map<String, String> payload) {
        if (payload == null || !payload.containsKey("otp") || payload.get("otp") == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "OTP is required"));
        }

        String inputOtp = payload.get("otp").trim();

        boolean isValid = otpService.validateOtpInRedis("PAY_OTP:" + paymentId, inputOtp);
        if (isValid) {
            paymentService.markPaymentAsCompleted(paymentId);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid or Expired OTP"));
        }
    }
}
