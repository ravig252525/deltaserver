package com.delta.server.delta.main;


import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class SelfPingScheduler {

    private final RestTemplate restTemplate;

    public SelfPingScheduler(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }


    // हर 10 मिनट (600000 मिलीसेकंड) में खुद को पिंग करें
    // ध्यान दें: आपको अपनी Render ऐप का वास्तविक URL यहां डालना होगा
    // उदाहरण के लिए, "https://your-render-app-name.onrender.com/ping"
    @Scheduled(fixedRateString = "PT12M", initialDelayString = "PT30S")
    // 30 seconds delay
    public void selfPing() {
        try {
            // अपनी Render ऐप का URL यहां डालें
            String appUrl = "https://deltaserver-bdru.onrender.com/ping"; // लोकल टेस्टिंग के लिए, Render पर डिप्लॉय करते समय बदलें
            String response = restTemplate.getForObject(appUrl, String.class);
            System.out.println("Self-ping successful: " + response);
        } catch (Exception e) {
            System.err.println("Self-ping failed: " + e.getMessage());
        }
    }
}
