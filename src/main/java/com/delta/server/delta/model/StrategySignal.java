package com.delta.server.delta.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class StrategySignal {
    public enum SignalType {ENTRY_LONG, ENTRY_SHORT, EXIT_LONG, EXIT_SHORT, STOP_LOSS, TAKE_PROFIT}
    private String id = UUID.randomUUID().toString();
    private String symbol;
    private String timeframe;
    private SignalType type;
    private BigDecimal price;
    private BigDecimal quantity;
    private Instant generatedAt = Instant.now();
    private String strategyId = "momentum-breakout";
    private boolean executed = false;
    private String orderId;
    private String positionId;// Linked order ID


}
