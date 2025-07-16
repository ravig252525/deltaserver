package com.delta.server.delta.advancewebsocket;


import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.springframework.lang.NonNull;



@Slf4j
public class AdvancedWebSocketClient {
    private final WebSocketConfig config;
    private final ScheduledExecutorService scheduler;
    private final Executor callbackExecutor;
    private final Queue<String> messageQueue = new ConcurrentLinkedQueue<>();
    private WebSocket webSocket;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean manuallyClosed = new AtomicBoolean(false);
    private long currentReconnectDelay;

    // RxJava subjects for events/messages/errors
    private final BehaviorSubject<ConnectionEvent> connectionSubject = BehaviorSubject.create();
    private final PublishSubject<String> textMessageSubject = PublishSubject.create();
    private final PublishSubject<ByteString> binaryMessageSubject = PublishSubject.create();
    private final PublishSubject<Throwable> errorSubject = PublishSubject.create();

    // Track subscription payloads to resend on reconnect
    private final Set<String> subscriptionPayloads = ConcurrentHashMap.newKeySet();

    // Heartbeat/watchdog
    private ScheduledFuture<?> heartbeatTimeoutFuture;
    private final long heartbeatTimeoutMs;

    // Ping scheduling (JSON ping)
    private ScheduledFuture<?> pingFuture;


    public AdvancedWebSocketClient(WebSocketConfig config) {
        this.config = config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.callbackExecutor = config.getCallbackExecutor();
        this.currentReconnectDelay = config.getReconnectInitialDelayMs();
        this.heartbeatTimeoutMs = config.getHeartbeatTimeoutMs();
    }

    /**
     * Observe connection events
     */
    public Observable<ConnectionEvent> observeConnection() {
        return connectionSubject.hide().subscribeOn(Schedulers.io());
    }

    /**
     * Observe text messages
     */
    public Observable<String> observeTextMessages() {
        return textMessageSubject.hide().subscribeOn(Schedulers.io())  ;
    }

    /**
     * Observe binary messages
     */
    public Observable<ByteString> observeBinaryMessages() {
        return binaryMessageSubject.hide().subscribeOn(Schedulers.io());
    }

    /**
     * Observe errors
     */
    public Observable<Throwable> observeErrors() {
        return errorSubject.hide().subscribeOn(Schedulers.io());
    }

    /**
     * Initiate connection; auto handles reconnect/resubscribe
     */
    public synchronized void connect() {
        manuallyClosed.set(false);
        log("Connecting...");
        callbackExecutor.execute(() -> connectionSubject.onNext(ConnectionEvent.CONNECTING));

        Request request = new Request.Builder().url(config.getUrl()).build();
        webSocket = config.getOkHttpClient().newWebSocket(request, createWebSocketListener());
        // connected flag set in onOpen
    }

    /**
     * Close explicitly
     */
    public synchronized Completable close(int code, String reason) {
        return Completable.fromAction(() -> {
            manuallyClosed.set(true);
            log("Closing by user: code=" + code + ", reason=" + reason);
            if (webSocket != null) {
                webSocket.close(code, reason);
            }
            cleanupPingHeartbeat();

            // Complete subjects
            textMessageSubject.onComplete();
            binaryMessageSubject.onComplete();
            connectionSubject.onComplete();
            errorSubject.onComplete();
            // Shutdown scheduler fully, since client lifecycle ends
            scheduler.shutdownNow();
        });
    }

    /**
     * Send text; if not connected, queue
     */
    public synchronized void send(String text) {
        if (connected.get() && webSocket != null) {
            webSocket.send(text);
        } else {
            if (messageQueue.size() >= config.getMaxQueueSize()) {
                messageQueue.poll(); // drop oldest
            }
            messageQueue.offer(text);
        }
    }

    /**
     * Track subscription payload; resend on reconnect
     */
    public void addSubscriptionPayload(String payload) {
        if (payload == null || payload.isEmpty()) return;
        subscriptionPayloads.add(payload);
        send(payload);
    }

    public void removeSubscriptionPayload(String payload) {
        if (payload == null || payload.isEmpty()) return;
        subscriptionPayloads.remove(payload);
        send(payload); // user-provided unsubscribe
    }

    /**
     * Flush queued messages when connected
     */
    private synchronized void flushQueue() {
        while (!messageQueue.isEmpty() && connected.get() && webSocket != null) {
            String msg = messageQueue.poll();
            webSocket.send(Objects.requireNonNull(msg));
        }
    }

    private WebSocketListener createWebSocketListener() {
        return new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket ws, @NonNull Response response) {
                connected.set(true);
                currentReconnectDelay = config.getReconnectInitialDelayMs();
                log("WebSocket opened");
                callbackExecutor.execute(() -> connectionSubject.onNext(ConnectionEvent.CONNECTED));

                // Flush queued messages
                flushQueue();
                // Resubscribe payloads
                if (!subscriptionPayloads.isEmpty()) {
                    for (String payload : subscriptionPayloads) {
                        ws.send(payload);
                    }
                }


                // JSON heartbeat protocol
                if (config.isUseJsonHeartbeat()) {
                    ws.send("{\"type\":\"enable_heartbeat\"}");
                    log("Sent JSON enable_heartbeat");
                }

                // JSON ping every interval
                if (config.isUseJsonPing() && config.getPingIntervalMs() > 0) {
                    if (pingFuture != null && !pingFuture.isDone()) {
                        pingFuture.cancel(false);
                    }
                    pingFuture = scheduler.scheduleWithFixedDelay(() -> {
                        if (connected.get()) {
                            ws.send("{\"type\":\"ping\"}");
                            log("Sent JSON ping");
                        }
                    }, config.getPingIntervalMs(), config.getPingIntervalMs(), TimeUnit.MILLISECONDS);
                }

                // Underlying OkHttpClient pingInterval has been set in config.getOkHttpClient()
                resetHeartbeatWatcher();
            }

            @Override
            public void onMessage(@NonNull WebSocket ws, @NonNull String text) {
                // JSON heartbeat?
                if (config.isUseJsonHeartbeat() && text.contains("\"type\":\"heartbeat\"")) {
                    log("Received JSON heartbeat");
                    resetHeartbeatWatcher();
                    return;
                }
                // JSON pong?
                if (config.isUseJsonPing() && text.contains("\"type\":\"pong\"")) {
                    log("Received JSON pong");
                    resetHeartbeatWatcher();
                    return;
                }
                // Forward other messages
                textMessageSubject.onNext(text);
            }

            @Override
            public void onMessage(@NonNull WebSocket ws, @NonNull ByteString bytes) {
                binaryMessageSubject.onNext(bytes);
            }

            // Note: onPing/onPong overrides removed because superclass doesn't declare them in this OkHttp version

            @Override
            public void onClosing(@NonNull WebSocket ws, int code, @NonNull String reason) {
                connected.set(false);
                log("WebSocket onClosing: code=" + code + ", reason=" + reason);
                callbackExecutor.execute(() -> connectionSubject.onNext(ConnectionEvent.DISCONNECTED));
                ws.close(code, reason);
                cleanupPingHeartbeat();
                if (!manuallyClosed.get() && config.isEnableAutoReconnect()) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onClosed(@NonNull WebSocket ws, int code, @NonNull String reason) {
                connected.set(false);
                log("WebSocket onClosed: code=" + code + ", reason=" + reason);
                callbackExecutor.execute(() -> connectionSubject.onNext(ConnectionEvent.DISCONNECTED));
                cleanupPingHeartbeat();
                if (!manuallyClosed.get() && config.isEnableAutoReconnect()) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t, Response response) {
                connected.set(false);
                if (response != null) {
                    log("WebSocket failure, code=" + response.code() + ", msg=" + response.message(), t);
                } else {
                    log("WebSocket failure (no response)", t);
                }
                callbackExecutor.execute(() -> errorSubject.onNext(t));
                callbackExecutor.execute(() -> connectionSubject.onNext(ConnectionEvent.FAILED));
                cleanupPingHeartbeat();
                if (!manuallyClosed.get() && config.isEnableAutoReconnect()) {
                    scheduleReconnect();
                }
            }
        };
    }

    /**
     * Reset watchdog timer for heartbeat/pong
     */
    private void resetHeartbeatWatcher() {
        if (heartbeatTimeoutFuture != null && !heartbeatTimeoutFuture.isDone()) {
            heartbeatTimeoutFuture.cancel(false);
        }
        heartbeatTimeoutFuture = scheduler.schedule(() -> {
            log("Heartbeat timeout reached; closing WebSocket");
            if (webSocket != null) {
                webSocket.close(1001, "Heartbeat timeout");
            }
        }, heartbeatTimeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Cancel ping & heartbeat timers
     */
    private void cleanupPingHeartbeat() {
        if (pingFuture != null) {
            pingFuture.cancel(false);
            pingFuture = null;
        }
        if (heartbeatTimeoutFuture != null) {
            heartbeatTimeoutFuture.cancel(false);
            heartbeatTimeoutFuture = null;
        }
    }

    /**
     * Schedule reconnect with exponential backoff
     */
    private void scheduleReconnect() {
        log("Scheduling reconnect in " + currentReconnectDelay + " ms");
        callbackExecutor.execute(() -> connectionSubject.onNext(ConnectionEvent.RECONNECTING));
        scheduler.schedule(() -> {
            log("Reconnecting now...");
            connect();
            // Double delay up to max
            currentReconnectDelay = Math.min(currentReconnectDelay * 2, config.getReconnectMaxDelayMs());
        }, currentReconnectDelay, TimeUnit.MILLISECONDS);
    }

    /**
     * Network callback with debounce
     */

    public static void log(String msg) {
        System.out.println("[AdvancedWebSocket] {}"+ msg);
        //Timber.tag(APPTAG).d("[AdvancedWebSocket] %s", msg);
    }

    public static void log(String msg, Throwable t) {
        // Timber.tag(APPTAG).d("[AdvancedWebSocket] %s %s", msg, t.getMessage());
        log.error("[AdvancedWebSocket] %s %s"+ msg, t.getMessage());
    }
}
