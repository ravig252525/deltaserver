package com.delta.server.delta.model;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

@Data
@Table("watchlist")
public class Watchlist {

    private String symbol;
    @Column("product_id")
    private Long productId;
    private String description;
    @Column("user_id")
    private String userId;
}
