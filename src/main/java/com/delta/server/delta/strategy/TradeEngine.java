package com.delta.server.delta.strategy;

import com.delta.server.delta.controller.TradingWebSocketController;
import com.delta.server.delta.model.*;
import com.delta.server.delta.model.StrategySignal;
import com.delta.server.delta.repo.CandleRepository;
import com.delta.server.delta.service.HistoricalDataManager;
import com.delta.server.delta.service.TechnicalAnalysisService;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class TradeEngine {
    private final CandleRepository candleRepo;
    private final MomentumBreakoutStrategy tradingStrategy;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final TradingWebSocketController wsController;

    public TradeEngine(CandleRepository candleRepo,
                       MomentumBreakoutStrategy tradingStrategy, TradingWebSocketController wsController) {
        this.candleRepo = candleRepo;
        this.tradingStrategy = tradingStrategy;
        this.wsController = wsController;

        // Start monitoring tasks
        scheduler.scheduleAtFixedRate(this::monitorPositions, 5, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::processSignals, 1, 1, TimeUnit.SECONDS);
    }

    public void processSignal(StrategySignal signal) {
        // Check for existing position
        boolean hasPosition = candleRepo.getOpenPositions().stream()
                .anyMatch(p -> p.getSymbol().equals(signal.getSymbol()));

        if (hasPosition && signal.getType().name().contains("ENTRY")) {
            System.out.println("Position already exists for " + signal.getSymbol());
            return;
        }

        switch (signal.getType()) {
            case ENTRY_LONG, ENTRY_SHORT -> executeEntrySignal(signal);
            case STOP_LOSS, TAKE_PROFIT, EXIT_LONG, EXIT_SHORT -> executeExitSignal(signal);
        }
    }

    private void processSignals() {
        List<StrategySignal> signals = candleRepo.getPendingSignals();
        for (StrategySignal signal : signals) {
            processSignal(signal);
        }
    }

    private void executeEntrySignal(StrategySignal signal) {
        try {
            // Create order
            Order order = new Order();
            order.setSymbol(signal.getSymbol());
            order.setType(Order.OrderType.MARKET);
            order.setSide(signal.getType() == StrategySignal.SignalType.ENTRY_LONG ?
                    Order.OrderSide.BUY : Order.OrderSide.SELL);
            order.setQuantity(signal.getQuantity());
            order.setStatus(Order.OrderStatus.FILLED);
            order.setPrice(signal.getPrice());
            order.setFilledQuantity(signal.getQuantity());

            // Create position
            Position position = new Position();
            position.setSymbol(signal.getSymbol());
            position.setEntryPrice(signal.getPrice());
            position.setQuantity(signal.getQuantity());
            position.setEntryOrderId(order.getId());

            // Calculate stop and take profit
            boolean isLong = signal.getType() == StrategySignal.SignalType.ENTRY_LONG;
            BigDecimal atrValue = getCurrentAtr(signal.getSymbol(), signal.getTimeframe());

            position.setStopPrice(tradingStrategy.getStopLossPrice(
                    signal.getPrice(), isLong, atrValue
            ));

            position.setTakeProfitPrice(tradingStrategy.getTakeProfitPrice(
                    signal.getPrice(), position.getStopPrice(), isLong
            ));

            // Save to Redis
            candleRepo.saveOrder(order);
            candleRepo.savePosition(position);
            wsController.sendPositionUpdate(position);
            // Update signal
            signal.setExecuted(true);
            signal.setOrderId(order.getId());
            signal.setPositionId(position.getId());
            candleRepo.saveSignal(signal);

            wsController.sendSignalUpdate(signal);

            System.out.println("Entered " + signal.getType() + " position for " +
                    signal.getSymbol() + " at " + signal.getPrice());

        } catch (Exception e) {
            System.err.println("Error executing entry signal: " + e.getMessage());
        }
    }

    private BigDecimal getCurrentAtr(String symbol, String timeframe) {
        String key = symbol + "|" + timeframe;
        List<Candle> candles = candleRepo.getSeries(key);
        if (candles == null || candles.isEmpty()) return BigDecimal.valueOf(100);

        BarSeries series = new TechnicalAnalysisService(null, null, null)
                .loadSeriesFrom(candles, HistoricalDataManager.parseTimeframe(timeframe));

        if (series.getBarCount() < 20) return BigDecimal.valueOf(100);

        ATRIndicator atr = new ATRIndicator(series, 14);
        return BigDecimal.valueOf(atr.getValue(series.getEndIndex()).doubleValue());
    }

    private void executeExitSignal(StrategySignal signal) {
        try {
            Position position = candleRepo.getOpenPositions().stream()
                    .filter(p -> p.getId().equals(signal.getPositionId()))
                    .findFirst()
                    .orElse(null);

            if (position == null) return;

            // Create exit order
            Order order = new Order();
            order.setSymbol(signal.getSymbol());
            order.setType(Order.OrderType.MARKET);
            order.setSide(signal.getType() == StrategySignal.SignalType.STOP_LOSS ||
                    signal.getType() == StrategySignal.SignalType.EXIT_SHORT ?
                    Order.OrderSide.SELL : Order.OrderSide.BUY);
            order.setQuantity(position.getQuantity());
            order.setStatus(Order.OrderStatus.FILLED);
            order.setPrice(signal.getPrice());
            order.setFilledQuantity(position.getQuantity());

            // Close position
            position.setStatus(Position.PositionStatus.CLOSED);
            position.setClosedAt(Instant.now());
            position.setExitOrderId(order.getId());
            position.setCurrentPrice(signal.getPrice());

            // Calculate PnL
            BigDecimal pnl = position.getQuantity().multiply(
                    signal.getPrice().subtract(position.getEntryPrice()));
            position.setUnrealizedPnl(pnl);

            // Save to Redis
            candleRepo.saveOrder(order);
            candleRepo.savePosition(position);
            wsController.sendPositionUpdate(position);

            // Update signal
            signal.setExecuted(true);
            signal.setOrderId(order.getId());
            candleRepo.saveSignal(signal);
            wsController.sendSignalUpdate(signal);
            System.out.println("Exited position for " + signal.getSymbol() +
                    " at " + signal.getPrice() + " PnL: " + pnl);

        } catch (Exception e) {
            System.err.println("Error executing exit signal: " + e.getMessage());
        }
    }

    public void monitorPositions() {
        List<Position> positions = candleRepo.getOpenPositions();
        for (Position position : positions) {
            BigDecimal currentPrice = getCurrentMarketPrice(position.getSymbol());
            position.setCurrentPrice(currentPrice);

            // Calculate unrealized PnL
            BigDecimal pnl = position.getQuantity().multiply(
                    currentPrice.subtract(position.getEntryPrice()));
            position.setUnrealizedPnl(pnl);

            // Check stop loss
            if (position.getQuantity().compareTo(BigDecimal.ZERO) > 0) { // Long
                if (currentPrice.compareTo(position.getStopPrice()) <= 0) {
                    triggerStopLoss(position);
                } else if (currentPrice.compareTo(position.getTakeProfitPrice()) >= 0) {
                    triggerTakeProfit(position);
                }
            } else { // Short
                if (currentPrice.compareTo(position.getStopPrice()) >= 0) {
                    triggerStopLoss(position);
                } else if (currentPrice.compareTo(position.getTakeProfitPrice()) <= 0) {
                    triggerTakeProfit(position);
                }
            }

            candleRepo.savePosition(position);
        }
    }

    private void triggerStopLoss(Position position) {
        StrategySignal signal = new StrategySignal();
        signal.setSymbol(position.getSymbol());
        signal.setType(StrategySignal.SignalType.STOP_LOSS);
        signal.setPrice(position.getStopPrice());
        signal.setQuantity(position.getQuantity().abs());
        signal.setPositionId(position.getId());
        candleRepo.saveSignal(signal);
    }

    private void triggerTakeProfit(Position position) {
        StrategySignal signal = new StrategySignal();
        signal.setSymbol(position.getSymbol());
        signal.setType(StrategySignal.SignalType.TAKE_PROFIT);
        signal.setPrice(position.getTakeProfitPrice());
        signal.setQuantity(position.getQuantity().abs());
        signal.setPositionId(position.getId());
        candleRepo.saveSignal(signal);
    }

    private BigDecimal getCurrentMarketPrice(String symbol) {
        // In a real implementation, this would come from the exchange
        return BigDecimal.valueOf(50000); // Placeholder
    }
}
