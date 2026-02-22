package com.example.demo.controller;

import com.example.demo.service.DemoDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Demo", description = "Demo data management (reset/seed)")
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoDataService demoDataService;

    @Operation(summary = "Reset demo data", description = "Deletes all data and re-seeds with sample expense reports and users")
    @PostMapping("/reset")
    public ResponseEntity<String> reset() {
        try {
            demoDataService.resetAndSeed();
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            // Log for Render logs; return a safe message to the client.
            e.printStackTrace();
            String msg = e.getMessage();
            if (msg == null || msg.isBlank()) msg = e.getClass().getSimpleName();
            return ResponseEntity.status(500).body("Demo reset failed: " + msg);
        }
    }
}
