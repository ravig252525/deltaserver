package com.delta.server.delta.service;


import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Objects;


@Service
public class RedisDataResetService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisDataResetService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // हर दिन सुबह 5 बजे Redis डेटा रीसेट करें
    @Scheduled(cron = "0 0 5 * * *") // Seconds Minutes Hours DayOfMonth Month DayOfWeek
    public void resetRedisDataDaily() {
        try {
            Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().flushDb(); // या flushAll()
            System.out.println("Redis data reset successfully at 5 AM.");
        } catch (Exception e) {
            System.err.println("Error resetting Redis data: " + e.getMessage());
        }
    }
}

