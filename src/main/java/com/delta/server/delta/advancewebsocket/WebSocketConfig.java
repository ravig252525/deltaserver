package com.delta.server.delta.advancewebsocket;


import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.Setter;
import okhttp3.OkHttpClient;
import org.springframework.lang.NonNull;

/**
 * Configuration holder for AdvancedWebSocketClient.
 */

@Setter
@Getter
public class WebSocketConfig {
    private final String url;
    private final OkHttpClient okHttpClient;
    private final long pingIntervalMs;
    private final long reconnectInitialDelayMs;
    private final long reconnectMaxDelayMs;
    private final boolean enableAutoReconnect;
    private final int maxQueueSize;
    private final Executor callbackExecutor;
    private final boolean useWebSocketPing;   // WebSocket-level ping-frame
    private final boolean useJsonPing;        // JSON-based ping/pong messages
    private final boolean useJsonHeartbeat;   // JSON-based heartbeat protocol
    private final long heartbeatTimeoutMs;    // watchdog timeout for heartbeat/pong

    private WebSocketConfig(Builder builder) {
        this.url = builder.url;
        this.okHttpClient = builder.okHttpClient;
        this.pingIntervalMs = builder.pingIntervalMs;
        this.reconnectInitialDelayMs = builder.reconnectInitialDelayMs;
        this.reconnectMaxDelayMs = builder.reconnectMaxDelayMs;
        this.enableAutoReconnect = builder.enableAutoReconnect;
        this.maxQueueSize = builder.maxQueueSize;
        this.callbackExecutor = builder.callbackExecutor;
        this.useWebSocketPing = builder.useWebSocketPing;
        this.useJsonPing = builder.useJsonPing;
        this.useJsonHeartbeat = builder.useJsonHeartbeat;
        this.heartbeatTimeoutMs = builder.heartbeatTimeoutMs;
    }


    public static class Builder {
        private String url;
        private OkHttpClient okHttpClient;
        private long pingIntervalMs = 0;
        private long reconnectInitialDelayMs = 1000;
        private long reconnectMaxDelayMs = 60000;
        private boolean enableAutoReconnect = true;
        private int maxQueueSize = 100;
        private Executor callbackExecutor;


        // नए विकल्प:
        private boolean useWebSocketPing = true;
        private boolean useJsonPing = false;
        private boolean useJsonHeartbeat = false;
        private long heartbeatTimeoutMs = 35000;

        /**
         * WebSocket URL (wss://...)
         */
        public Builder url(@NonNull String url) {
            this.url = url;
            return this;
        }

        /**
         * OkHttpClient instance; यदि नहीं दिया गया, तो build() में एक बनाएगा।
         */
        public Builder okHttpClient(@NonNull OkHttpClient client) {
            this.okHttpClient = client;
            return this;
        }

        /**
         * WebSocket-level ping-frame उपयोग करना है या नहीं।
         */
        public Builder useWebSocketPing(boolean use) {
            this.useWebSocketPing = use;
            return this;
        }

        /**
         * JSON-based ping/pong मैसेज उपयोग करना है या नहीं।
         */
        public Builder useJsonPing(boolean use) {
            this.useJsonPing = use;
            return this;
        }

        /**
         * JSON-based heartbeat protocol (“enable_heartbeat” भेजना, और सर्वर से “heartbeat” मैसेज रिसीव करना)।
         */
        public Builder useJsonHeartbeat(boolean use) {
            this.useJsonHeartbeat = use;
            return this;
        }

        /**
         * Ping या heartbeat के इंटरवल के लिए टाइम: हर pingIntervalMs पर या WebSocket ping-frame या JSON ping भेजेगा।
         */
        public Builder pingInterval(long interval, TimeUnit unit) {
            this.pingIntervalMs = unit.toMillis(interval);
            return this;
        }

        /**
         * Auto-reconnect enable/disable
         */
        public Builder autoReconnect(boolean enable) {
            this.enableAutoReconnect = enable;
            return this;
        }

        /**
         * Reconnect के लिए initial और max delay
         */
        public Builder reconnectDelay(long initialDelay, long maxDelay, TimeUnit unit) {
            this.reconnectInitialDelayMs = unit.toMillis(initialDelay);
            this.reconnectMaxDelayMs = unit.toMillis(maxDelay);
            return this;
        }

        /**
         * Send queue max size if not connected
         */
        public Builder maxQueueSize(int size) {
            this.maxQueueSize = size;
            return this;
        }

        /**
         * Executor जिसपर callback events भेजे जाएँ (RxJava subjects को onNext)
         */
        public Builder callbackExecutor(@NonNull Executor executor) {
            this.callbackExecutor = executor;
            return this;
        }


        /**
         * Heartbeat/pong timeout threshold (millis) यदि JSON heartbeat या ping/pong protocol है।
         */
        public Builder heartbeatTimeout(long timeout, TimeUnit unit) {
            this.heartbeatTimeoutMs = unit.toMillis(timeout);
            return this;
        }

        public WebSocketConfig build() {
            if (url == null) throw new IllegalStateException("url required");
            if (okHttpClient == null) {
                // यदि खुद client नहीं दिया, तो default with pingInterval and no read-timeout
                OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                        .readTimeout(0, TimeUnit.MILLISECONDS);
                if (pingIntervalMs > 0) {
                    clientBuilder.pingInterval(pingIntervalMs, TimeUnit.MILLISECONDS);
                }
                this.okHttpClient = clientBuilder.build();
            }
            if (callbackExecutor == null) {
                this.callbackExecutor = Executors.newSingleThreadExecutor();
            }
            return new WebSocketConfig(this);
        }
    }
}
