package com.delta.server.delta.controller;

import com.delta.server.delta.model.Tick;
import com.delta.server.delta.model.Watchlist;
import com.delta.server.delta.service.WatchlistService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@RestController
@RequestMapping("/api/users/{userId}/watchlist")
public class WatchlistController {
    private final WatchlistService svc;

    public WatchlistController(WatchlistService svc) {
        this.svc = svc;
    }


    @GetMapping(path = "/stream/ticker", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Tick> streamTicker(@PathVariable String userId) {
        return svc.streamTicker(userId);
    }


    @GetMapping(path = "/stream/watchlist", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Watchlist> stream(@PathVariable String userId) {
        return svc.streamUser(userId);
    }

    // Fetch initial list (or reactively same as stream)
    @GetMapping
    public Flux<Watchlist> getAll(@PathVariable String userId) {
        return svc.streamUser(userId);
    }

    @PostMapping
    public Mono<Watchlist> add(
            @PathVariable String userId,
            @RequestParam String symbol,
            @RequestParam Long productId,
            @RequestParam(required = false) String description
    ) {
        // build Watchlist object explicitly
        Watchlist w = new Watchlist();
        w.setUserId(userId);
        w.setSymbol(symbol);
        w.setProductId(productId);
        w.setDescription(description);
        return svc.addSymbol(w);
    }

    @DeleteMapping("/{id}")
    public Mono<Void> delete(
            @PathVariable String userId,
            @RequestParam String symbol,
            @RequestParam Long productId,
            @RequestParam(required = false) String description) {
        // pass a minimal Watchlist with id & userId
        Watchlist w = new Watchlist();
        w.setUserId(userId);
        w.setSymbol(symbol);
        w.setProductId(productId);
        w.setDescription(description);
        return svc.removeSymbol(w);
    }

}
