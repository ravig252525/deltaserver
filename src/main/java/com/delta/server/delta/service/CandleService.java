package com.delta.server.delta.service;

import com.delta.server.delta.controller.TradingWebSocketController;
import com.delta.server.delta.model.Candle;
import com.delta.server.delta.model.Tick;
import com.delta.server.delta.repo.CandleRepository;
import io.reactivex.rxjava3.core.Completable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;


@Service
public class CandleService {
    private final CandleRepository candleRepo;
    private final TechnicalAnalysisService analysisService;
    private final TradingWebSocketController wsController;

    public CandleService(CandleRepository candleRepo, TechnicalAnalysisService analysisService, TradingWebSocketController wsController) {
        this.candleRepo = candleRepo;
        this.analysisService = analysisService;
        this.wsController = wsController;
    }

    @Async
    public Completable updateOrAppendTick(String key, String timeframe, Tick tick) {
        return Completable.create(emitter -> {
            List<Candle> candles = candleRepo.getSeries(key);
            if (candles == null) {
                candles = new ArrayList<>();
            }

            long millis = tick.getTimestamp() / 1000; // Microseconds to milliseconds
            long bucketStart = computeBucketStart(millis, timeframe);
            Instant candleStart = Instant.ofEpochSecond(bucketStart);

            synchronized (candles) {
                if (!candles.isEmpty()) {
                    Candle last = candles.get(candles.size() - 1);
                    if (candleStart.equals(last.getBeginTime())) {
                        // Update existing candle
                        last.setHighPrice(Math.max(last.getHighPrice(), tick.getPrice()));
                        last.setLowPrice(Math.min(last.getLowPrice(), tick.getPrice()));
                        last.setClosePrice(tick.getPrice());
                        last.setVolume(last.getVolume() + tick.getVolume());
                    } else if (candleStart.isAfter(last.getBeginTime())) {
                        // Create new candle
                        Duration period = HistoricalDataManager.parseTimeframe(timeframe);
                        candles.add(new Candle(period, candleStart, tick.getPrice(), tick.getPrice(), tick.getPrice(), tick.getPrice(), tick.getVolume(), tick.getProductId()));
                    }
                } else {
                    Duration period = HistoricalDataManager.parseTimeframe(timeframe);
                    candles.add(new Candle(period, candleStart, tick.getPrice(), tick.getPrice(), tick.getPrice(), tick.getPrice(), tick.getVolume(), tick.getProductId()));
                }


                // Trim candles to last 500 only
                if (candles.size() > 500) {
                    candles = new ArrayList<>(candles.subList(candles.size() - 500, candles.size()));
                }
                candleRepo.saveSeries(key, candles);
                if (!candles.isEmpty()) {
                    wsController.sendCandleUpdate(candles.get(candles.size() - 1), key);
                }
                System.out.println("updateOrAppendTick: " + key + ", candles: " + candles.size());
                analysisService.analyzeSeries(key, candles);
                emitter.onComplete();
            }
        });
    }

    public static long computeBucketStart(long epochMillis, String timeframe) {
        Instant instant = Instant.ofEpochMilli(epochMillis);
        ZonedDateTime zdt = ZonedDateTime.ofInstant(instant, HistoricalDataManager.TZ_IST);
        return switch (timeframe) {
            case "1d" -> zdt.truncatedTo(ChronoUnit.DAYS).toEpochSecond();
            case "1h", "4h" ->
                    zdt.truncatedTo(ChronoUnit.HOURS).withMinute(0).withSecond(0).withNano(0).toEpochSecond();
            default -> {
                int minutes = Integer.parseInt(timeframe.replace("m", ""));
                yield zdt.truncatedTo(ChronoUnit.HOURS).withMinute(zdt.getMinute() / minutes * minutes).withSecond(0).withNano(0).toEpochSecond();
            }
        };
    }

    public RedisTemplate<String, List<Candle>> getredis() {
        return candleRepo.getredis();
    }
}
