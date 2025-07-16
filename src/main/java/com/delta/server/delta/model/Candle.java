package com.delta.server.delta.model;

import lombok.*;
import org.springframework.data.redis.core.RedisHash;
import java.time.Duration;
import java.time.Instant;

@Data
@NoArgsConstructor  // Add this
@AllArgsConstructor
@RedisHash("candle")
public class Candle {
    private Duration timePeriod;
    private Instant beginTime;
    private double openPrice;
    private double highPrice;
    private double lowPrice;
    private double closePrice;
    private long volume;
    private long product_id;

}

