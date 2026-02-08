package com.example.demo.controller;

import com.example.demo.service.DemoDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoDataService demoDataService;

    @PostMapping("/reset")
    public ResponseEntity<String> reset() {
        try {
            demoDataService.resetAndSeed();
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            // Log for Render logs; return a safe message to the client.
            e.printStackTrace();
            return ResponseEntity.status(500).body("Demo reset failed");
        }
    }
}
