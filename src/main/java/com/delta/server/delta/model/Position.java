package com.delta.server.delta.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
public class Position {
    public enum PositionStatus {OPEN, CLOSED}

    private String id = UUID.randomUUID().toString();
    private String symbol;
    private BigDecimal entryPrice;
    private BigDecimal currentPrice;
    private BigDecimal quantity;
    private BigDecimal unrealizedPnl = BigDecimal.ZERO;
    private PositionStatus status = PositionStatus.OPEN;
    private Instant openedAt = Instant.now();
    private Instant closedAt;
    private String entryOrderId;
    private String exitOrderId;
    private String strategyId = "momentum-breakout";
    private BigDecimal stopPrice;
    private BigDecimal takeProfitPrice;

}
