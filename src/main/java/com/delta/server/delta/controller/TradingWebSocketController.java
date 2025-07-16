package com.delta.server.delta.controller;

import com.delta.server.delta.model.Candle;
import com.delta.server.delta.model.Position;
import com.delta.server.delta.model.StrategySignal;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TradingWebSocketController {
    private final SimpMessagingTemplate messagingTemplate;


    public TradingWebSocketController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }


    // Called by service when new candle data arrives
    public void sendCandleUpdate(Candle candle, String key) {
        String topic = "/topic/candles/" + key;
        messagingTemplate.convertAndSend(topic, candle);
    }

    // Send position updates
    public void sendPositionUpdate(Position position) {
        messagingTemplate.convertAndSend("/topic/positions", position);
    }

    // Send signal updates
    public void sendSignalUpdate(StrategySignal signal) {
        messagingTemplate.convertAndSend("/topic/signals", signal);
    }
}
