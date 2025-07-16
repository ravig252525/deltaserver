package com.delta.server.delta.strategy;

import com.delta.server.delta.model.StrategySignal;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;

@Service
public class MomentumBreakoutStrategy extends TradingStrategy {
    private static final int FAST_EMA = 20;
    public static final int SLOW_EMA = 50;
    public static final int RSI_PERIOD = 14;
    private static final double RSI_OVERBOUGHT = 70;
    public static final double RSI_OVERSOLD = 30;
    public static final int BREAKOUT_LOOKBACK = 5;
    public static final int ATR_PERIOD = 14;
    private static final double ATR_MULTIPLIER = 1.5;

    public MomentumBreakoutStrategy() {
        super("momentum-breakout", "Momentum Breakout", "BTCUSD",
                Arrays.asList("5m", "15m"), BigDecimal.valueOf(10000));
    }

    @Override
    public int getMinBarsRequired() {
        // Calculate minimum bars needed for all indicators
        return Math.max(SLOW_EMA, Math.max(RSI_PERIOD, ATR_PERIOD)) + BREAKOUT_LOOKBACK + 10;
    }

    @Override
    public StrategySignal generateSignal(BarSeries series, String timeframe) {
        if (series.getBarCount() < SLOW_EMA + 10) return null;

        int lastIndex = series.getEndIndex();
        int prevIndex = lastIndex - 1;

        // EMA Indicators
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator fastEma = new EMAIndicator(closePrice, FAST_EMA);
        EMAIndicator slowEma = new EMAIndicator(closePrice, SLOW_EMA);

        // RSI Indicator
        RSIIndicator rsi = new RSIIndicator(closePrice, RSI_PERIOD);

        // ATR for stop loss
        ATRIndicator atr = new ATRIndicator(series, ATR_PERIOD);

        // Volume Indicator
        VolumeIndicator volume = new VolumeIndicator(series);

        // Trend direction
        boolean uptrend = fastEma.getValue(lastIndex).isGreaterThan(slowEma.getValue(lastIndex));
        boolean downtrend = fastEma.getValue(lastIndex).isLessThan(slowEma.getValue(lastIndex));

        // RSI conditions
        boolean rsiOkLong = rsi.getValue(lastIndex).doubleValue() < RSI_OVERBOUGHT;
        boolean rsiOkShort = rsi.getValue(lastIndex).doubleValue() > RSI_OVERSOLD;

        // Breakout levels
        double recentHigh = series.getSubSeries(Math.max(0, prevIndex - BREAKOUT_LOOKBACK), prevIndex)
                .getBarData().stream()
                .mapToDouble(bar -> bar.getHighPrice().doubleValue())
                .max()
                .orElse(0);

        double recentLow = series.getSubSeries(Math.max(0, prevIndex - BREAKOUT_LOOKBACK), prevIndex)
                .getBarData().stream()
                .mapToDouble(bar -> bar.getLowPrice().doubleValue())
                .min()
                .orElse(Double.MAX_VALUE);

        // Breakout conditions
        Bar prevBar = series.getBar(prevIndex);
        Bar lastBar = series.getBar(lastIndex);

        boolean breakoutLong = prevBar.getClosePrice().doubleValue() > recentHigh &&
                lastBar.getClosePrice().doubleValue() > prevBar.getClosePrice().doubleValue();

        boolean breakoutShort = prevBar.getClosePrice().doubleValue() < recentLow &&
                lastBar.getClosePrice().doubleValue() < prevBar.getClosePrice().doubleValue();

        // Volume spike
        double volAvg = series.getSubSeries(Math.max(0, prevIndex - BREAKOUT_LOOKBACK), prevIndex)
                .getBarData().stream()
                .mapToDouble(bar -> bar.getVolume().doubleValue())
                .average()
                .orElse(0);

        boolean volSpike = prevBar.getVolume().doubleValue() > volAvg * 1.5;

        // Generate signals
        StrategySignal signal = new StrategySignal();
        signal.setSymbol(series.getName());
        signal.setTimeframe(timeframe);
        signal.setGeneratedAt(Instant.now());
        signal.setStrategyId(this.id);

        if (uptrend && rsiOkLong && breakoutLong && volSpike) {
            signal.setType(StrategySignal.SignalType.ENTRY_LONG);
            signal.setPrice(BigDecimal.valueOf(lastBar.getClosePrice().doubleValue()));

            // Calculate stop loss
            BigDecimal atrValue = BigDecimal.valueOf(atr.getValue(lastIndex).doubleValue());
            BigDecimal stopLoss = signal.getPrice().subtract(atrValue.multiply(BigDecimal.valueOf(ATR_MULTIPLIER)));

            // Position sizing
            BigDecimal size = calculatePositionSize(signal.getPrice(), stopLoss);
            signal.setQuantity(size);

            return signal;
        } else if (downtrend && rsiOkShort && breakoutShort && volSpike) {
            signal.setType(StrategySignal.SignalType.ENTRY_SHORT);
            signal.setPrice(BigDecimal.valueOf(lastBar.getClosePrice().doubleValue()));

            // Calculate stop loss
            BigDecimal atrValue = BigDecimal.valueOf(atr.getValue(lastIndex).doubleValue());
            BigDecimal stopLoss = signal.getPrice().add(atrValue.multiply(BigDecimal.valueOf(ATR_MULTIPLIER)));

            // Position sizing
            BigDecimal size = calculatePositionSize(signal.getPrice(), stopLoss);
            signal.setQuantity(size);

            return signal;
        }
        return null;
    }

    @Override
    public BarSeries getCurrentSeries() {
        return null;
    }


    public BigDecimal getStopLossPrice(BigDecimal entryPrice, boolean isLong) {
        // ATR-based stop loss calculation
        BarSeries series = getCurrentSeries(); // Need to implement this
        if (series == null || series.getBarCount() < 1) return entryPrice;

        ATRIndicator atr = new ATRIndicator(series, ATR_PERIOD);
        BigDecimal atrValue = BigDecimal.valueOf(atr.getValue(series.getEndIndex()).doubleValue());

        if (isLong) {
            return entryPrice.subtract(atrValue.multiply(BigDecimal.valueOf(ATR_MULTIPLIER)));
        } else {
            return entryPrice.add(atrValue.multiply(BigDecimal.valueOf(ATR_MULTIPLIER)));
        }
    }


    public BigDecimal getTakeProfitPrice(BigDecimal entryPrice, BigDecimal stopLoss, boolean isLong) {
        // 2:1 risk-reward ratio
        BigDecimal risk = entryPrice.subtract(stopLoss).abs();
        if (isLong) {
            return entryPrice.add(risk.multiply(BigDecimal.valueOf(2)));
        } else {
            return entryPrice.subtract(risk.multiply(BigDecimal.valueOf(2)));
        }
    }


}
