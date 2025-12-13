package com.example.auto_git_be.controller;

import com.example.auto_git_be.dto.*;
import com.example.auto_git_be.service.AuthService;
import com.example.auto_git_be.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthService authService;
    
    @Autowired
    private EmailService emailService;

    /**
     * Get Google OAuth URL
     */
    @GetMapping("/google/url")
    public ResponseEntity<GoogleAuthUrlResponse> getGoogleAuthUrl() {
        return ResponseEntity.ok(authService.getGoogleAuthUrl());
    }

    /**
     * Handle Google OAuth callback - GET (from browser redirect)
     * Shows authorization code for user to copy and paste into VSCode
     */
    @GetMapping("/google/callback")
    public ResponseEntity<String> handleGoogleCallbackGet(@RequestParam("code") String code) {
        try {
            System.out.println("=== Google Callback GET ===");
            System.out.println("Authorization Code: " + code);
            
            // Return simple HTML page with authorization code
            String html = "<html><head><meta charset='UTF-8'><title>Google Login</title>" +
                "<style>" +
                "body{font-family:Arial,sans-serif;max-width:600px;margin:50px auto;padding:30px;background:#f5f5f5}" +
                ".card{background:white;padding:30px;border-radius:8px;box-shadow:0 2px 8px rgba(0,0,0,0.1)}" +
                ".success{color:#28a745;font-size:28px;margin-bottom:20px;text-align:center}" +
                ".step{background:#e7f3ff;padding:15px;border-radius:5px;margin:20px 0;border-left:4px solid #007bff}" +
                ".code-box{background:#f8f9fa;padding:20px;border-radius:5px;margin:20px 0;border:2px dashed #007bff;text-align:center}" +
                ".code{font-family:monospace;font-size:14px;word-break:break-all;color:#d63384;font-weight:bold}" +
                "button{background:#007bff;color:white;border:none;padding:12px 24px;border-radius:5px;cursor:pointer;font-size:16px;width:100%}" +
                ".copied{color:#28a745;font-weight:bold;text-align:center;margin-top:10px;display:none}" +
                ".warning{background:#fff3cd;border-left:4px solid #ffc107;padding:15px;margin-top:20px;border-radius:5px}" +
                "</style></head><body><div class='card'>" +
                "<div class='success'>✓ Đăng nhập Google thành công!</div>" +
                "<div class='step'><strong>📋 Bước 1:</strong> Click nút để copy authorization code</div>" +
                "<div class='code-box'><div class='code' id='authCode'>" + code + "</div></div>" +
                "<button onclick='copyCode()'>📋 Copy Authorization Code</button>" +
                "<div class='copied' id='copiedMsg'>✓ Đã copy! Quay lại VSCode</div>" +
                "<div class='step' style='margin-top:20px'><strong>🔙 Bước 2:</strong> Paste code vào VSCode</div>" +
                "<div class='warning'>⚠️ Code chỉ dùng được 1 lần và hết hạn sau vài phút</div>" +
                "</div><script>" +
                "function copyCode(){" +
                "const code=document.getElementById('authCode').innerText;" +
                "navigator.clipboard.writeText(code).then(()=>{" +
                "document.getElementById('copiedMsg').style.display='block'" +
                "})}" +
                "window.onload=function(){" +
                "const el=document.getElementById('authCode');" +
                "const range=document.createRange();" +
                "range.selectNodeContents(el);" +
                "window.getSelection().removeAllRanges();" +
                "window.getSelection().addRange(range)" +
                "}" +
                "</script></body></html>";
            
            System.out.println("HTML generated successfully, length: " + html.length());
            
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (Exception e) {
            System.err.println("Error in Google callback GET: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_HTML)
                    .body("<html><body><h1>❌ Login Failed</h1><p>" + e.getMessage() + "</p></body></html>");
        }
    }

    /**
     * Handle Google OAuth callback - POST (from Extension)
     * Extension sends code via POST request body
     */
    @PostMapping("/google/callback")
    public ResponseEntity<LoginResponse> handleGoogleCallbackPost(@RequestBody GoogleAuthCodeRequest request) {
        try {
            String role = request.getRole() != null ? request.getRole() : "STUDENT";
            LoginResponse response = authService.handleGoogleCallback(request.getCode(), role);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(LoginResponse.builder()
                    .token(null)
                    .email(null)
                    .name(e.getMessage())
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Verify JWT token
     */
    @GetMapping("/verify")
    public ResponseEntity<Void> verifyToken(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            boolean isValid = authService.verifyToken(token);
            
            if (isValid) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.status(401).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }
    
    /**
     * OTP Login - Step 1: Request OTP
     * User provides Google email, backend sends OTP
     */
    @PostMapping("/otp/request")
    public ResponseEntity<OTPResponse> requestOTP(@RequestBody OTPLoginRequest request) {
        try {
            String email = request.getEmail();
            
            // Validate email format
            if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                return ResponseEntity.badRequest()
                        .body(OTPResponse.builder()
                                .success(false)
                                .message("Invalid email format")
                                .build());
            }
            
            // Generate and send OTP
            emailService.generateAndSendOTP(email);
            
            return ResponseEntity.ok(OTPResponse.builder()
                    .success(true)
                    .message("Mã OTP đã được gửi đến " + email + ". Vui lòng kiểm tra hộp thư.")
                    .build());
                    
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(OTPResponse.builder()
                            .success(false)
                            .message("Failed to send OTP: " + e.getMessage())
                            .build());
        }
    }
    
    /**
     * OTP Login - Step 2: Verify OTP and return JWT
     * User provides email + OTP, backend verifies and returns token
     */
    @PostMapping("/otp/verify")
    public ResponseEntity<LoginResponse> verifyOTP(@RequestBody OTPVerifyRequest request) {
        try {
            String email = request.getEmail();
            String otp = request.getOtp();
            String role = request.getRole() != null ? request.getRole() : "STUDENT";
            
            // Verify OTP
            boolean isValid = emailService.verifyOTP(email, otp);
            
            if (!isValid) {
                return ResponseEntity.status(401).build();
            }
            
            // OTP valid - create/get user and generate JWT
            LoginResponse response = authService.loginWithEmail(email, role);
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(LoginResponse.builder()
                    .token(null)
                    .email(null)
                    .name(e.getMessage())
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
