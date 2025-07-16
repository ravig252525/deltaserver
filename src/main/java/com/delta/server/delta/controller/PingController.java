package com.delta.server.delta.controller;


import com.delta.server.delta.model.Candle;
import com.delta.server.delta.repo.CandleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PingController {

    private final CandleRepository candleRepo;

    public PingController(CandleRepository candleRepo) {
        this.candleRepo = candleRepo;
    }


    @GetMapping("/history")
    public ResponseEntity<List<Candle>> getHistoricalCandles(@RequestParam String symbol, @RequestParam String timeframe) {
        List<Candle> candles = candleRepo.getSeries(symbol + "|" + timeframe);
        return ResponseEntity.ok(candles);
    }

}