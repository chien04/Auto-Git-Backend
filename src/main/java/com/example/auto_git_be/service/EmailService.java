package com.example.auto_git_be.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EmailService {
    
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    
    private final JavaMailSender mailSender;

    private final StringRedisTemplate redisTemplate;
    
    @Value("${spring.mail.username}")
    private String fromEmail;

    private static final String OTP_KEY_PREFIX = "otp:";
    
    @Value("${otp.expiration.minutes:5}")
    private long otpExpirationMinutes;

    public void generateAndSendOTP(String email) {
        try {
            String otp = String.format("%06d", new Random().nextInt(999999));
            
            redisTemplate.opsForValue().set(buildOTPKey(email), otp, otpExpirationMinutes, TimeUnit.MINUTES);
            
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
        String key = buildOTPKey(email);
        String storedOTP = redisTemplate.opsForValue().get(key);
        
        if (storedOTP == null) {
            return false;
        }
        
        boolean valid = storedOTP.equals(otp);
        
        if (valid) {
            redisTemplate.delete(key);
        }
        
        return valid;
    }

    private String buildOTPKey(String email) {
        return OTP_KEY_PREFIX + email;
    }
}
