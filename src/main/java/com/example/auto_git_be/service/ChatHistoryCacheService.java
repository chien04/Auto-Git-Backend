package com.example.auto_git_be.service;

import com.example.auto_git_be.dto.chat.ChatMessageDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatHistoryCacheService {

    private static final int MAX_HISTORY = 10;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private String buildKey(Long userId) {
        return String.format("chat:history:user:%d", userId);
    }

    public void pushMessage(Long userId, String role, String message) throws JsonProcessingException {
        String key = buildKey(userId);

        Map<String, String> messageObj = Map.of(
                "role", role,
                "content", message
        );
        String jsonMessage = objectMapper.writeValueAsString(messageObj);

        redisTemplate.executePipelined((RedisCallback<?>) connection -> {
            byte[] rawKey = key.getBytes();
            byte[] rawValue = jsonMessage.getBytes();

            connection.listCommands().rPush(rawKey, rawValue);
            connection.listCommands().lTrim(rawKey, -MAX_HISTORY, -1);
            connection.keyCommands().expire(rawKey, 24 * 3600);

            return null;
        });
    }

    public List<String> getRecentMessages(Long userId) {
        String key = buildKey(userId);
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    public void clearHistory(Long userId) {
        redisTemplate.delete(buildKey(userId));
    }

}
