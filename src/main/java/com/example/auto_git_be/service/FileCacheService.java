package com.example.auto_git_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileCacheService {

    private final StringRedisTemplate redisTemplate;

    private static final long CACHE_EXPIRATION = 7;

    private String buildKey(Long userId, String assignmentCode, String studentName, String filename) {
        return String.format("file_hash:%d:%s:%s:%s", userId, assignmentCode, studentName, filename);
    }

    public boolean isIdentical(Long userId, String assignmentCode, String studentName, String taskOrderNo, String newHash) {
        String key = buildKey(userId, assignmentCode, studentName, taskOrderNo);
        String savedHash = redisTemplate.opsForValue().get(key);

        if (savedHash != null && savedHash.equals(newHash)) {
            log.info("Cache hit: File {} không thay đổi.", taskOrderNo);
            return true;
        }
        return false;
    }

    public void updateCache(Long userId, String assignmentCode, String studentName, String taskOrderNo, String newHash) {
        String key = buildKey(userId, assignmentCode, studentName, taskOrderNo);
        redisTemplate.opsForValue().set(key, newHash, CACHE_EXPIRATION, TimeUnit.DAYS);
        log.info("Cache updated: Đã lưu hash mới cho file {}", taskOrderNo);
    }

}
