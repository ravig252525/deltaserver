package com.delta.server.delta.controller;

import com.delta.server.delta.model.Watchlist;
import com.delta.server.delta.model.Candle;
import com.delta.server.delta.repo.CandleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.delta.server.delta.repo.WatchlistRepository;
import java.util.List;
import reactor.core.publisher.Flux;
@RestController
@RequestMapping("/api")
public class PingController {

    private final CandleRepository candleRepo;

    private final WatchlistRepository watchlistRepo;

    public PingController(CandleRepository candleRepo, WatchlistRepository watchlistRepo) {
        this.candleRepo = candleRepo;
        this.watchlistRepo = watchlistRepo;
    }


    @GetMapping("/history")
    public ResponseEntity<List<Candle>> getHistoricalCandles(@RequestParam String symbol, @RequestParam String timeframe) {
        List<Candle> candles = candleRepo.getSeries(symbol + "|" + timeframe);
        return ResponseEntity.ok(candles);
    }

    @GetMapping("/watchlists")
    public Flux<Watchlist> getWatchLists(String userid) {
        return watchlistRepo.findAllByUserId(userid);
    }

}
