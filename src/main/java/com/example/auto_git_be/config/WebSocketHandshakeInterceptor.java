package com.example.auto_git_be.config;

import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.repository.UserRepository;
import com.example.auto_git_be.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;
    
    private final UserRepository userRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            String token = servletRequest.getServletRequest().getParameter("token");
            
            if (token != null && !token.isEmpty()) {
                try {
                    // Verify JWT token and extract user email
                    String email = jwtService.extractEmail(token);
                    
                    if (email != null && !jwtService.isTokenExpired(token)) {
                        // Find user by email to get userId
                        User user = userRepository.findByEmail(email).orElse(null);
                        
                        if (user != null) {
                            // Store BOTH email and userId in session attributes
                            attributes.put("userEmail", email);
                            attributes.put("userId", user.getId().toString());
                            
                            return true;
                        } else {
                            return false;
                        }
                    }
                } catch (Exception e) {
                    return false;
                }
            }
        }
        
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                              WebSocketHandler wsHandler, Exception exception) {
        // Nothing to do after handshake
    }
}
