package com.example.auto_git_be.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Email service for sending OTP codes via real email
 */
@Service
public class EmailService {
    
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    
    @Autowired
    private JavaMailSender mailSender;
    
    @Value("${spring.mail.username}")
    private String fromEmail;

    // use redis for production
    private final Map<String, OTPData> otpStore = new ConcurrentHashMap<>();
    
    @Value("${otp.expiration.minutes:5}")
    private long otpExpirationMinutes;

    public void generateAndSendOTP(String email) {
        try {
            String otp = String.format("%06d", new Random().nextInt(999999));
            
            long expirationTime = System.currentTimeMillis() + (otpExpirationMinutes * 60 * 1000);
            otpStore.put(email, new OTPData(otp, expirationTime));
            
            sendOTPEmail(email, otp);

        } catch (Exception e) {
            throw new RuntimeException("Failed to send OTP email: " + e.getMessage());
        }
    }

    private void sendOTPEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Auto Git Classroom - Mã OTP đăng nhập");
        message.setText(String.format(
            "Xin chào,\n\n" +
            "Mã OTP của bạn là: %s\n\n" +
            "Mã này có hiệu lực trong %d phút.\n" +
            "Vui lòng không chia sẻ mã này với bất kỳ ai.\n\n" +
            "Nếu bạn không yêu cầu mã này, vui lòng bỏ qua email này.\n\n" +
            "Trân trọng,\n" +
            "Auto Git Classroom Team",
            otp, otpExpirationMinutes
        ));
        
        mailSender.send(message);
    }
    
    /**
     * Send general email
     */
    public void sendEmail(String toEmail, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage());
        }
    }

    public boolean verifyOTP(String email, String otp) {
        OTPData storedOTP = otpStore.get(email);
        
        if (storedOTP == null) {
            return false;
        }
        
        if (System.currentTimeMillis() > storedOTP.expirationTime) {
            otpStore.remove(email);
            return false;
        }
        
        boolean valid = storedOTP.otp.equals(otp);
        
        if (valid) {
            otpStore.remove(email);
        }
        
        return valid;
    }
    
    /**
     * Clear expired OTPs (call this periodically)
     */
    public void clearExpiredOTPs() {
        long now = System.currentTimeMillis();
        otpStore.entrySet().removeIf(entry -> now > entry.getValue().expirationTime);
    }
    
    private static class OTPData {
        String otp;
        long expirationTime;
        
        OTPData(String otp, long expirationTime) {
            this.otp = otp;
            this.expirationTime = expirationTime;
        }
    }
}
