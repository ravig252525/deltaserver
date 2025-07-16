package com.delta.server.delta.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
public class Order {

    public enum OrderType {MARKET, LIMIT, STOP, TAKE_PROFIT}
    public enum OrderStatus {PENDING, FILLED, CANCELLED, REJECTED}
    public enum OrderSide {BUY, SELL}
    private String id = UUID.randomUUID().toString();
    private String symbol;
    private OrderType type;
    private OrderSide side;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal filledQuantity = BigDecimal.ZERO;
    private OrderStatus status = OrderStatus.PENDING;
    private Instant createdAt = Instant.now();
    private Instant updatedAt;
    private String parentOrderId;
    private String strategyId = "momentum-breakout";


}
