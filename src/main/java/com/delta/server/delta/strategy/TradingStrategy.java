package com.delta.server.delta.strategy;

import com.delta.server.delta.model.StrategySignal;
import org.ta4j.core.BarSeries;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;


public abstract class TradingStrategy {
    protected String id;
    protected String name;
    protected String symbol;
    protected List<String> timeframes;
    protected BigDecimal initialCapital;
    protected BigDecimal currentCapital;
    protected BigDecimal maxRiskPerTrade = BigDecimal.valueOf(0.01);
    protected BigDecimal maxPositionSize = BigDecimal.valueOf(0.05);

    public TradingStrategy(String id, String name, String symbol,
                           List<String> timeframes, BigDecimal initialCapital) {
        this.id = id;
        this.name = name;
        this.symbol = symbol;
        this.timeframes = timeframes;
        this.initialCapital = initialCapital;
        this.currentCapital = initialCapital;
    }

    public abstract StrategySignal generateSignal(BarSeries series, String timeframe);

    public abstract BarSeries getCurrentSeries();


    public BigDecimal calculatePositionSize(BigDecimal entryPrice, BigDecimal stopLossPrice) {
        BigDecimal riskAmount = currentCapital.multiply(maxRiskPerTrade);
        BigDecimal riskPerUnit = entryPrice.subtract(stopLossPrice).abs();

        if (riskPerUnit.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal size = riskAmount.divide(riskPerUnit, 8, RoundingMode.HALF_UP);
        BigDecimal maxSize = currentCapital.multiply(maxPositionSize)
                .divide(entryPrice, 8, RoundingMode.HALF_UP);

        return size.min(maxSize);
    }


    public BigDecimal getStopLossPrice(BigDecimal entryPrice, boolean isLong, BigDecimal atrValue) {
        if (isLong) {
            return entryPrice.subtract(atrValue.multiply(BigDecimal.valueOf(1.5)));
        } else {
            return entryPrice.add(atrValue.multiply(BigDecimal.valueOf(1.5)));
        }
    }

    public BigDecimal getTakeProfitPrice(BigDecimal entryPrice, BigDecimal stopLoss, boolean isLong) {
        BigDecimal risk = entryPrice.subtract(stopLoss).abs();
        if (isLong) {
            return entryPrice.add(risk.multiply(BigDecimal.valueOf(2)));
        } else {
            return entryPrice.subtract(risk.multiply(BigDecimal.valueOf(2)));
        }
    }


    public int getMinBarsRequired() {
        return 100; // Default implementation
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public List<String> getTimeframes() {
        return timeframes;
    }

    public void setTimeframes(List<String> timeframes) {
        this.timeframes = timeframes;
    }

    public BigDecimal getInitialCapital() {
        return initialCapital;
    }

    public void setInitialCapital(BigDecimal initialCapital) {
        this.initialCapital = initialCapital;
    }

    public BigDecimal getCurrentCapital() {
        return currentCapital;
    }

    public void setCurrentCapital(BigDecimal currentCapital) {
        this.currentCapital = currentCapital;
    }

    public BigDecimal getMaxRiskPerTrade() {
        return maxRiskPerTrade;
    }

    public void setMaxRiskPerTrade(BigDecimal maxRiskPerTrade) {
        this.maxRiskPerTrade = maxRiskPerTrade;
    }

    public BigDecimal getMaxPositionSize() {
        return maxPositionSize;
    }

    public void setMaxPositionSize(BigDecimal maxPositionSize) {
        this.maxPositionSize = maxPositionSize;
    }
// Getters and setters
}
