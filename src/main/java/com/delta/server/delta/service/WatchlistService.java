package com.delta.server.delta.service;

import com.delta.server.delta.advancewebsocket.ConnectionEvent;
import com.delta.server.delta.advancewebsocket.WebSocketConfig;
import com.delta.server.delta.advancewebsocket.WebSocketManager;
import com.delta.server.delta.model.Tick;
import com.delta.server.delta.model.WatchListEvent;
import com.delta.server.delta.model.Watchlist;
import com.delta.server.delta.repo.WatchlistRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.delta.server.delta.advancewebsocket.AdvancedWebSocketClient.log;
import static com.delta.server.delta.service.SubscriptionService.WEBSOCKET_URL;

@Service
public class WatchlistService {
    private final WatchlistRepository repo;

    private WebSocketManager marketWebSocket;
    private final Set<String> subscribed = ConcurrentHashMap.newKeySet();
    private final SubscriptionService subscriptionService;
    private final Sinks.Many<WatchListEvent> sink;
    private final Sinks.Many<Tick> ticker = Sinks.many().multicast().onBackpressureBuffer();


    public WatchlistService(WatchlistRepository repo, SubscriptionService subscriptionService) {
        this.repo = repo;
        this.subscriptionService = subscriptionService;
        this.sink = Sinks.many().multicast().onBackpressureBuffer();
    }

    public Flux<Watchlist> streamUser(String userId) {
        // initial load for user
        Flux<Watchlist> initial = repo.findAllByUserId(userId);
        // updates filtered by user
        Flux<Watchlist> updates = sink.asFlux()
                .filter(evt -> evt.getUserId().equals(userId))
                .flatMap(evt -> evt.isDeleted()
                        ? Mono.empty()
                        : repo.findById(evt.getId())
                );
        return Flux.concat(initial, updates);
    }

    /**
     * Ensure the WebSocket is up; if not, (re)connect.
     */
    private synchronized Mono<Void> ensureConnected(String userId) {
        return Mono.defer(() -> {
            if (marketWebSocket == null) {
                connectWebSocket(userId);
            }
            return Mono.empty();
        });
    }


    public void connectWebSocket(String userId) {
        WebSocketConfig marketConfig = new WebSocketConfig.Builder().url(WEBSOCKET_URL).useJsonHeartbeat(true)             // JSON heartbeat प्रोटोकॉल उपयोग करना है?
                .useJsonPing(true)                  // JSON ping/pong मैसेज उपयोग?
                .useWebSocketPing(false)            // WebSocket-level ping-frame नहीं भेजना (यदि सर्वर JSON चाहता हो)
                .pingInterval(30, TimeUnit.SECONDS) // हर 30s पर JSON ping भेजेगा
                .reconnectDelay(1000, 60000, TimeUnit.MILLISECONDS).autoReconnect(true).maxQueueSize(200).callbackExecutor(Executors.newSingleThreadExecutor()).heartbeatTimeout(35, TimeUnit.SECONDS).build();

        marketWebSocket = new WebSocketManager(marketConfig, new WebSocketManager.WebSocketListener() {
            @Override
            public void onConnectionEvent(ConnectionEvent event) {
                if (event == ConnectionEvent.CONNECTED) {
                    resubscribeAll(userId);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                log("marketWebSocket ", throwable);
            }

            @Override
            public void onMessage(String message) {
                JsonNode n = null;
                try {
                    n = new ObjectMapper().readTree(message);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                if ("v2/ticker".equals(n.get("type").asText())) {
                    String sym = n.get("symbol").asText();
                    double ltp = n.get("mark_price").asDouble();
                    double ch24 = n.get("mark_change_24h").asDouble();
                    // double close = n.get("close").asDouble();
                    long product_id = n.get("product_id").asLong();
                    long volume = n.get("volume").asLong();
                    long timestamp = n.get("timestamp").asLong();
                    Tick tick = new Tick(sym, ltp, volume, timestamp, product_id, ch24);
                    // क्लाइंट को भेजें
                    ticker.emitNext(tick, Sinks.EmitFailureHandler.FAIL_FAST);
                }
            }
        });
        marketWebSocket.connect();
    }

    private void resubscribeAll(String userid) {
        System.out.println("Initializing listener on thread: " + Thread.currentThread().getName());
        repo.findAllByUserId(userid).doOnNext(this::subscribe).subscribe();
    }


    public void subscribe(Watchlist watchlist) {
        System.out.println("subscribe symbol: " + watchlist);
        if (subscribed.add(watchlist.getSymbol())) {
            marketWebSocket.addSubscription(buildSubscribeMessage(watchlist.getSymbol()));
            subscriptionService.manageHistpricals(watchlist.getSymbol(), watchlist.getProductId());
        }
    }

    private JSONObject buildSubscribeMessage(String symbol) {
        try {
            JSONObject root = new JSONObject().put("type", "subscribe");
            JSONObject payload = new JSONObject();
            JSONArray channels = new JSONArray();
            JSONObject chanObj = new JSONObject().put("name", "v2/ticker").put("symbols", new JSONArray(Collections.singletonList(symbol)));
            channels.put(chanObj);
            payload.put("channels", channels);
            root.put("payload", payload);
            return root;
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to build subscribe JSON", e);
        }
    }

    public void unsubscribe(Watchlist watchlist) {
        if (subscribed.remove(watchlist.getSymbol())) {
            marketWebSocket.removeSubscription(buildUnsubscribePayload(watchlist.getSymbol()));
        }
    }

    private JSONObject buildUnsubscribePayload(String symbol) {
        try {
            JSONObject root = new JSONObject().put("type", "unsubscribe");
            JSONObject payload = new JSONObject();
            JSONArray channels = new JSONArray();
            JSONObject chanObj = new JSONObject().put("name", "v2/ticker").put("symbols", new JSONArray(Collections.singletonList(symbol)));
            channels.put(chanObj);
            payload.put("channels", channels);
            root.put("payload", payload);
            return root;
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to build subscribe JSON", e);
        }
    }


    // === यूज़र ने UI से ऐड किया ==
    public Mono<Watchlist> addSymbol(Watchlist watchlist) {
        return ensureConnected(watchlist.getUserId()).then(repo.findBySymbolForUser(watchlist.getSymbol(), watchlist.getUserId()).switchIfEmpty(Mono.defer(() -> repo.save(watchlist))))
                .doOnNext(saved -> {
                    subscribe(saved);
                    sink.emitNext(new WatchListEvent(watchlist.getId(), false, watchlist.getUserId()), Sinks.EmitFailureHandler.FAIL_FAST);
                });
    }


    // === यूज़र ने UI से हटाया ===

    /**
     * Remove a symbol—guarded by socket-init as well.
     */
    public Mono<Void> removeSymbol(Watchlist watchlist) {
        return ensureConnected(watchlist.getUserId()).then(repo.findBySymbolForUser(watchlist.getSymbol(), watchlist.getUserId()).flatMap(entry -> repo.delete(entry).doOnSuccess(v -> {
            unsubscribe(watchlist);
            sink.emitNext(new WatchListEvent(watchlist.getId(), true, watchlist.getUserId()), Sinks.EmitFailureHandler.FAIL_FAST);
        }))).then();
    }

    public Flux<Tick> streamTicker(String userId) {
        return ensureConnected(userId).thenMany(ticker.asFlux());
    }
}
