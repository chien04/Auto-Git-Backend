package com.example.auto_git_be.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null) {
            
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                // Get userId from session attributes (set during handshake)
                String userId = (String) accessor.getSessionAttributes().get("userId");
                
                if (userId != null) {
                    // Create a Principal with userId as the name
                    Principal principal = () -> userId;
                    accessor.setUser(principal);
                    
                } else {
                }
            } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                Principal user = accessor.getUser();
                String sessionId = accessor.getSessionId();
            } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                Principal user = accessor.getUser();
                String destination = accessor.getDestination();
                String sessionId = accessor.getSessionId();
            } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                Principal user = accessor.getUser();
                String destination = accessor.getDestination();
            }
        }
        
        return message;
    }
}
