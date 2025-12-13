package com.example.auto_git_be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OTPVerifyRequest {
    private String email;
    private String otp;
    private String role; // "TEACHER" or "STUDENT"
}
