package com.delta.server.delta.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("watchlist")
public class Watchlist {

    @Id
    private Long id;
    private String symbol;
    private Long productId;
    private String description;
    private String userId;
}
