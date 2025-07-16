package com.delta.server.delta.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/ping")
    public String ping() {
        System.out.println("Received a ping request to keep the server awake.");
        return "Pong from Delta Server!";
    }
}
