package com.delta.server.delta.repo;

import com.delta.server.delta.model.Candle;
import com.delta.server.delta.model.Order;
import com.delta.server.delta.model.Position;
import com.delta.server.delta.model.StrategySignal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;


@Repository
public class CandleRepository {

    private final RedisTemplate<String, List<Candle>> candleTemplate;
    private final RedisTemplate<String, Object> objectTemplate;

    public CandleRepository(
            @Qualifier("candleRedisTemplate") RedisTemplate<String, List<Candle>> candleTemplate,
            @Qualifier("redisTemplate") RedisTemplate<String, Object> objectTemplate) {

        this.candleTemplate = candleTemplate;
        this.objectTemplate = objectTemplate;
    }

    public List<Candle> getSeries(String key) {
        return candleTemplate.opsForValue().get(key);
    }

    public void saveSeries(String key, List<Candle> candles) {
        candleTemplate.opsForValue().set(key, candles);
    }

    public RedisTemplate<String, List<Candle>> getredis() {
        return candleTemplate;
    }


    public void saveSignal(StrategySignal signal) {
        objectTemplate.opsForValue().set("signal:" + signal.getId(), signal);
    }

    public void saveOrder(Order order) {
        objectTemplate.opsForValue().set("order:" + order.getId(), order);
    }

    public void savePosition(Position position) {
        objectTemplate.opsForValue().set("position:" + position.getId(), position);
    }


    public List<StrategySignal> getSignals() {
        Set<String> keys = objectTemplate.keys("signal:*");
        if (keys.isEmpty()) return Collections.emptyList();

        List<Object> objects = objectTemplate.opsForValue().multiGet(keys);
        return Objects.requireNonNull(objects).stream()
                .filter(obj -> obj instanceof StrategySignal)
                .map(obj -> (StrategySignal) obj)
                .collect(Collectors.toList());
    }

    public List<StrategySignal> getPendingSignals() {
        return getSignals().stream()
                .filter(signal -> !signal.isExecuted())
                .collect(Collectors.toList());
    }

    public List<Position> getOpenPositions() {
        Set<String> keys = objectTemplate.keys("position:*");
        if (keys.isEmpty()) return Collections.emptyList();

        List<Object> objects = objectTemplate.opsForValue().multiGet(keys);
        return Objects.requireNonNull(objects).stream()
                .filter(obj -> obj instanceof Position)
                .map(obj -> (Position) obj)
                .filter(position -> position.getStatus() == Position.PositionStatus.OPEN)
                .collect(Collectors.toList());
    }


}
