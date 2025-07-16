package com.delta.server.delta.service;

import com.delta.server.delta.model.StrategySignal;
import com.delta.server.delta.model.Candle;
import com.delta.server.delta.repo.CandleRepository;
import com.delta.server.delta.strategy.MomentumBreakoutStrategy;
import com.delta.server.delta.strategy.TradeEngine;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.delta.server.delta.service.HistoricalDataManager.TZ_IST;

@Service
public class TechnicalAnalysisService {

    private final CandleRepository candleRepo;
    private final TradeEngine tradeEngine;
    private final MomentumBreakoutStrategy tradingStrategy;
    private final Map<String, BarSeries> seriesMap = new ConcurrentHashMap<>();

    public TechnicalAnalysisService(CandleRepository candleRepo,
                                    TradeEngine tradeEngine,
                                    MomentumBreakoutStrategy tradingStrategy) {
        this.candleRepo = candleRepo;
        this.tradeEngine = tradeEngine;
        this.tradingStrategy = tradingStrategy;
    }


    public void analyzeSeries(String key, List<Candle> candles) {
        int minBars = tradingStrategy.getMinBarsRequired();
        if (candles.size() < minBars) {
            System.out.println("Not enough bars for analysis. Need: " + minBars + ", Have: " + candles.size());
            return;
        }

        // Only process last 50 candles
        List<Candle> recent = candles.subList(Math.max(0, candles.size() - 200), candles.size());
        // Determine timeframe from key
        String[] parts = key.split("\\|");
        String symbol = parts[0];
        String timeframe = parts[1];

        BarSeries series = loadSeriesFrom(recent, HistoricalDataManager.parseTimeframe(timeframe));
        seriesMap.put(key, series);
        // Generate signal
        StrategySignal signal = tradingStrategy.generateSignal(series, timeframe);
        if (signal != null) {
            System.out.println("Generated signal: " + signal.getType() + " for " + symbol);
            signal.setId(UUID.randomUUID().toString());
            candleRepo.saveSignal(signal);
            tradeEngine.processSignal(signal);
        }
    }


    public BarSeries loadSeriesFrom(List<Candle> candleList, Duration timeframe) {
        // 2) build empty series
        BaseBarSeries series = new BaseBarSeriesBuilder().withName("room_candles_" + timeframe.toString()).build();
        // 3) map each Room entity → Ta4j Bar with  period
        candleList.forEach(candle -> {
            ZonedDateTime endTime = ZonedDateTime.ofInstant(candle.getBeginTime(), TZ_IST);
            series.barBuilder().timePeriod(timeframe).endTime(endTime.toInstant()).openPrice(candle.getOpenPrice()).highPrice(candle.getHighPrice()).lowPrice(candle.getLowPrice()).closePrice(candle.getClosePrice()).volume(candle.getVolume()).amount(0.0).add();
        });
        return series;
    }


}
