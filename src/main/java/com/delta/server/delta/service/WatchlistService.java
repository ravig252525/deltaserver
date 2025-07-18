package com.delta.server.delta.service;

import com.delta.server.delta.advancewebsocket.ConnectionEvent;
import com.delta.server.delta.advancewebsocket.WebSocketConfig;
import com.delta.server.delta.advancewebsocket.WebSocketManager;
import com.delta.server.delta.model.Watchlist;
import com.delta.server.delta.repo.WatchlistRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.WebSocket;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.delta.server.delta.advancewebsocket.AdvancedWebSocketClient.log;
import static com.delta.server.delta.service.SubscriptionService.WEBSOCKET_URL;

@Service
public class WatchlistService {
    private final WatchlistRepository repo;
    private final SimpMessagingTemplate templ;
    private WebSocketManager marketWebSocket;
    private final Set<String> subscribed = ConcurrentHashMap.newKeySet();
    private final SubscriptionService subscriptionService;

    public WatchlistService(WatchlistRepository repo, SubscriptionService subscriptionService, SimpMessagingTemplate templ) {
        this.repo = repo;
        this.templ = templ;
        this.subscriptionService = subscriptionService;
    }

    /**
     * Ensure the WebSocket is up; if not, (re)connect.
     */
    private synchronized Mono<Void> ensureConnected() {
        return Mono.defer(() -> {
            if (marketWebSocket == null) {
                connectWebSocket();
            }
            return Mono.empty();
        });
    }


    public void connectWebSocket() {
        WebSocketConfig marketConfig = new WebSocketConfig.Builder().url(WEBSOCKET_URL).useJsonHeartbeat(true)             // JSON heartbeat प्रोटोकॉल उपयोग करना है?
                .useJsonPing(true)                  // JSON ping/pong मैसेज उपयोग?
                .useWebSocketPing(false)            // WebSocket-level ping-frame नहीं भेजना (यदि सर्वर JSON चाहता हो)
                .pingInterval(30, TimeUnit.SECONDS) // हर 30s पर JSON ping भेजेगा
                .reconnectDelay(1000, 60000, TimeUnit.MILLISECONDS).autoReconnect(true).maxQueueSize(200).callbackExecutor(Executors.newSingleThreadExecutor()).heartbeatTimeout(35, TimeUnit.SECONDS).build();

        marketWebSocket = new WebSocketManager(marketConfig, new WebSocketManager.WebSocketListener() {
            @Override
            public void onConnectionEvent(ConnectionEvent event) {
                if (event == ConnectionEvent.CONNECTED) {
                    resubscribeAll();
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
                    // क्लाइंट को भेजें
                    templ.convertAndSend("/topic/prices", Map.of("symbol", sym, "ltp", ltp, "change24", ch24));
                }
            }
        });
        marketWebSocket.connect();
    }

    private void resubscribeAll() {
        System.out.println("Initializing listener on thread: " + Thread.currentThread().getName());
        repo.findAll().doOnNext(this::subscribe).subscribe();
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


    public Flux<Watchlist> listForUser(String userId) {
        return repo.findAllByUserId(userId).doOnNext(wl -> templ.convertAndSend("/topic/watchlist", Map.of("type", "ADD", "symbol", wl.getSymbol())));
    }

    // === यूज़र ने UI से ऐड किया ==
    public Mono<Watchlist> addSymbol(Watchlist watchlist) {
        return ensureConnected().then(repo.findBySymbolAndUserId(watchlist.getSymbol(), watchlist.getUserId()).switchIfEmpty(Mono.defer(() -> repo.save(watchlist)))).doOnNext(saved -> {
            subscribe(saved);
            templ.convertAndSend("/topic/watchlist", Map.of("type", "ADD", "symbol", saved.getSymbol()));
        });
    }


    // === यूज़र ने UI से हटाया ===

    /**
     * Remove a symbol—guarded by socket-init as well.
     */
    public Mono<Void> removeSymbol(Watchlist watchlist) {
        return ensureConnected().then(repo.findBySymbolAndUserId(watchlist.getSymbol(), watchlist.getUserId()).flatMap(entry -> repo.delete(entry).doOnSuccess(v -> {
            unsubscribe(watchlist);
            templ.convertAndSend("/topic/watchlist", Map.of("type", "REMOVE", "symbol", watchlist.getSymbol()));
        }))).then();
    }
}
