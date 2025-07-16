package com.delta.server.delta.advancewebsocket;


import io.reactivex.rxjava3.schedulers.Schedulers;
import org.json.JSONObject;


import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class WebSocketManager {
    public interface WebSocketListener {
        void onConnectionEvent(ConnectionEvent event);

        void onError(Throwable throwable);

        void onMessage(String message);
    }

    private final AdvancedWebSocketClient webSocketClient;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final WebSocketListener listener;

    public WebSocketManager(WebSocketConfig config, WebSocketListener listener) {
        this.listener = listener;
        this.webSocketClient = new AdvancedWebSocketClient(config);
    }

    public void connect() {
        disposables.add(webSocketClient.observeConnection()
                .observeOn(Schedulers.newThread())
                .subscribe(listener::onConnectionEvent,
                        listener::onError));

        disposables.add(webSocketClient.observeErrors()
                .observeOn(Schedulers.single())
                .subscribe(listener::onError,
                        listener::onError));

        disposables.add(webSocketClient.observeTextMessages()
                .observeOn(Schedulers.single())
                .subscribe(listener::onMessage,
                        listener::onError));

        webSocketClient.connect();
    }

    public Completable disconnect(int code, String reason) {
        disposables.clear();
        return webSocketClient.close(code, reason);
    }

    public void send(JSONObject json) {
        webSocketClient.send(json.toString());
    }

    public void addSubscription(JSONObject subscription) {
        webSocketClient.addSubscriptionPayload(subscription.toString());
    }

    public void removeSubscription(JSONObject subscription) {
        webSocketClient.removeSubscriptionPayload(subscription.toString());
    }
}