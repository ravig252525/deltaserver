package com.delta.server.delta.service;

import com.delta.server.delta.model.Tick;
import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

import static com.delta.server.delta.advancewebsocket.AdvancedWebSocketClient.log;


@Service
public class SubscriptionService {
    private static final List<String> TIMEFRAMELIST = List.of("5m", "15m");
    public static final String WEBSOCKET_URL = "wss://socket.india.delta.exchange";
    private final CandleService candleService;
    private final HistoricalDataManager historicalDataManager;
    // symbol for which historical load complete
    private final ConcurrentHashMap<String, Queue<Tick>> buffer = new ConcurrentHashMap<>();
    private final Set<String> historicalComplete = ConcurrentHashMap.newKeySet();
    private final CompositeDisposable disposables = new CompositeDisposable();


    @Autowired
    public SubscriptionService(HistoricalDataManager historicalDataManager, CandleService candleService) {
        this.historicalDataManager = historicalDataManager;
        this.candleService = candleService;
    }


    public void clearDatabase() {
        // Uses RedisTemplate’s callback to send FLUSHDB
        candleService.getredis().execute(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(@NotNull RedisConnection connection) throws DataAccessException {
                connection.serverCommands().flushAll();
                return null;
            }
        });
    }


    public void processMarketMessage(String message) {
        log("massage " + message);
        try {
            Flowable.just(message)
                    // parse the JSON
                    .flatMap(text -> {
                        Tick tick = parseTick(text);
                        return tick != null ? Flowable.just(tick) : Flowable.empty();
                    })
                    // drop null parses
                    .filter(Objects::nonNull).parallel().sequential()
                    // for each Tick, run all timeframes in sequence
                    .concatMapCompletable(tick -> Flowable.fromIterable(TIMEFRAMELIST).concatMapCompletable(time -> {
                        String key = buildKey(tick.symbol, time);
                        if (!historicalComplete.contains(key)) {
                            buffer.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>()).add(tick);
                            return Completable.complete();
                        } else {
                            // your existing DB/network work
                            return candleService.updateOrAppendTick(key, time, tick);
                        }
                    }))
                    // do the whole thing on an I/O thread
                    .subscribeOn(Schedulers.io())
                    // if you need results back on a single thread
                    .observeOn(Schedulers.single())
                    // actually kick it off
                    .subscribe(() -> log("all ticks update  successfully"), err -> System.err.println("Error processing tick: " + err));
        } catch (JSONException e) {
            System.err.println("Error parsing market message: " + e);
        }
    }


    private Tick parseTick(@NotNull String text) throws JSONException {
        JSONObject root = new JSONObject(text);
        String type = root.optString("type", "");
        if (!"v2/ticker".equals(type)) return null;
        String symbol = root.getString("symbol");
        long timestampMicros = root.getLong("timestamp");
        double price = root.getDouble("close");
        long volume = root.getLong("volume");
        double markChange24hStr = root.getDouble("mark_change_24h");
        long product_id = root.getLong("product_id");
        return new Tick(symbol, price, volume, timestampMicros, product_id, markChange24hStr);
    }


    public void manageHistpricals(String symbol, Long product_id) {
        Flowable.fromIterable(TIMEFRAMELIST).flatMap(timeframe -> {
            String key = buildKey(symbol, timeframe);
            if (historicalComplete.contains(key)) {
                return Flowable.empty();
            }
            return historicalDataManager.fetchAndStore(symbol, timeframe, product_id).andThen(flushBufferForSymbolRx(key)).doOnComplete(() -> historicalComplete.add(key)).toFlowable().subscribeOn(Schedulers.io());
        }, 8).subscribeOn(Schedulers.io()).observeOn(Schedulers.single()).subscribe(o -> System.out.printf("Historical data loaded for %s", o), throwable -> System.out.println("Historical fetch failed for %s"));
    }


    public Completable flushBufferForSymbolRx(String key) {
        Queue<Tick> buffered = buffer.get(key);
        if (buffered == null || buffered.isEmpty()) {
            return Completable.complete();
        }

        return Flowable.fromIterable(buffered).flatMapCompletable(tick -> Flowable.fromIterable(TIMEFRAMELIST).flatMapCompletable(timeframe -> candleService.updateOrAppendTick(key, timeframe, tick), false, 8).subscribeOn(Schedulers.io()), false, 8).subscribeOn(Schedulers.io());

    }


    public static String buildKey(String symbol, String timeframe) {
        return symbol + "|" + timeframe;
    }


}
