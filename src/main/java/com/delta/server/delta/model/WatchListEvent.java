package com.delta.server.delta.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WatchListEvent {
    private final Long id;
    private final boolean deleted;
    private final String userId;


}
