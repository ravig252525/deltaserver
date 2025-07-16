package com.delta.server.delta.controller;

import com.delta.server.delta.model.Watchlist;
import com.delta.server.delta.service.WatchlistService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@RestController
public class WatchlistController {
    private final WatchlistService svc;

    public WatchlistController(WatchlistService svc) {
        this.svc = svc;
    }

    @MessageMapping("/watchlist/{userId}")
    public Flux<Watchlist> initialList(@PathVariable String userId) {
        return svc.listForUser(userId);
    }


    @MessageMapping("/add")
    public Mono<Void> addSymbol(Watchlist watchlist) {
        return svc.addSymbol(watchlist).then();
    }

    @MessageMapping("/remove")
    public Mono<Void> remove(Watchlist watchlist) {
        return svc.removeSymbol(watchlist);
    }
}
