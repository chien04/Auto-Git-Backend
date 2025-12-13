package com.example.auto_git_be.service;

import com.example.auto_git_be.dto.GoogleAuthUrlResponse;
import com.example.auto_git_be.dto.LoginResponse;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String redirectUri;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Generate Google OAuth URL
     */
    public GoogleAuthUrlResponse getGoogleAuthUrl() {
        // Use URLEncoder to properly encode redirect URI
        String encodedRedirectUri = "http%3A%2F%2Flocalhost%3A3000";
        
        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=" + googleClientId +
                "&redirect_uri=" + encodedRedirectUri +
                "&response_type=code" +
                "&scope=openid%20email%20profile" +
                "&access_type=offline" +
                "&prompt=consent"; // Force consent screen to avoid cache issues

        System.out.println("Generated OAuth URL: " + authUrl);
        
        return GoogleAuthUrlResponse.builder()
                .authUrl(authUrl)
                .build();
    }

    /**
     * Handle Google OAuth callback with role selection
     */
    public LoginResponse handleGoogleCallback(String code, String requestedRole) {
        // Exchange code for access token
        Map<String, Object> tokenResponse = exchangeCodeForToken(code);
        String accessToken = (String) tokenResponse.get("access_token");

        // Get user info from Google
        Map<String, Object> userInfo = getUserInfoFromGoogle(accessToken);

        // Extract user details
        String email = (String) userInfo.get("email");
        String name = (String) userInfo.get("name");
        String googleId = (String) userInfo.get("sub");
        String picture = (String) userInfo.get("picture");

        // Find existing user
        User user = userRepository.findByGoogleId(googleId).orElse(null);
        
        if (user != null) {
            // User exists - check role compatibility
            if (!user.getRole().equals(User.UserRole.BOTH)) {
                // User already has a specific role
                String existingRole = user.getRole().toString();
                if (!existingRole.equals(requestedRole)) {
                    throw new RuntimeException("Tài khoản này đã đăng ký với vai trò " + 
                        (existingRole.equals("TEACHER") ? "Giáo viên" : "Sinh viên") + 
                        ". Không thể đăng nhập với vai trò khác.");
                }
            } else {
                // User has BOTH role - update to requested role
                user.setRole(User.UserRole.valueOf(requestedRole));
                userRepository.save(user);
            }
        } else {
            // Create new user with requested role
            user = User.builder()
                    .email(email)
                    .name(name)
                    .googleId(googleId)
                    .profilePicture(picture)
                    .role(User.UserRole.valueOf(requestedRole))
                    .build();
            user = userRepository.save(user);
        }

        // Generate JWT token
        String jwtToken = jwtService.generateToken(user.getEmail(), user.getId());

        return LoginResponse.builder()
                .token(jwtToken)
                .email(user.getEmail())
                .name(user.getName())
                .userId(user.getId().toString())
                .role(user.getRole().toString())
                .build();
    }

    /**
     * Exchange authorization code for access token
     */
    private Map<String, Object> exchangeCodeForToken(String code) {
        String tokenUrl = "https://oauth2.googleapis.com/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // IMPORTANT: redirect_uri must match EXACTLY with the one used in OAuth URL
        String redirectUriForExchange = "http://localhost:3000";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", googleClientId);
        params.add("client_secret", googleClientSecret);
        params.add("redirect_uri", redirectUriForExchange);
        params.add("grant_type", "authorization_code");

        System.out.println("Exchanging code for token with redirect_uri: " + redirectUriForExchange);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
        return response.getBody();
    }

    /**
     * Get user information from Google
     */
    private Map<String, Object> getUserInfoFromGoogle(String accessToken) {
        // Use OpenID Connect userinfo endpoint (v3) for better field support
        String userInfoUrl = "https://www.googleapis.com/oauth2/v3/userinfo";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                userInfoUrl,
                HttpMethod.GET,
                entity,
                Map.class
        );
        return response.getBody();
    }

    /**
     * Verify JWT token
     */
    public boolean verifyToken(String token) {
        try {
            String email = jwtService.extractEmail(token);
            return userRepository.existsByEmail(email) && !jwtService.isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Login with email only (for OTP flow) with role selection
     */
    public LoginResponse loginWithEmail(String email, String requestedRole) {
        // Find existing user by email
        User user = userRepository.findByEmail(email).orElse(null);
        
        if (user != null) {
            // User exists - check role compatibility
            if (!user.getRole().equals(User.UserRole.BOTH)) {
                // User already has a specific role
                String existingRole = user.getRole().toString();
                if (!existingRole.equals(requestedRole)) {
                    throw new RuntimeException("Tài khoản này đã đăng ký với vai trò " + 
                        (existingRole.equals("TEACHER") ? "Giáo viên" : "Sinh viên") + 
                        ". Không thể đăng nhập với vai trò khác.");
                }
            } else {
                // User has BOTH role - update to requested role
                user.setRole(User.UserRole.valueOf(requestedRole));
                userRepository.save(user);
            }
        } else {
            // Create new user with requested role
            user = User.builder()
                    .email(email)
                    .name(email.split("@")[0]) // Use email prefix as name
                    .role(User.UserRole.valueOf(requestedRole))
                    .build();
            user = userRepository.save(user);
        }
        
        // Generate JWT token
        String jwtToken = jwtService.generateToken(user.getEmail(), user.getId());
        
        return LoginResponse.builder()
                .token(jwtToken)
                .email(user.getEmail())
                .name(user.getName())
                .userId(user.getId().toString())
                .role(user.getRole().toString())
                .build();
    }

    /**
     * Get user from token
     */
    public User getUserFromToken(String token) {
        String email = jwtService.extractEmail(token);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
