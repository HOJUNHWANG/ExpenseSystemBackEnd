package com.example.demo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "Health check endpoints")
@RestController
public class HealthController {

    /**
     * Render will sometimes probe / directly. Returning 200 avoids confusing 500s on the service root.
     */
    @Operation(summary = "Root health check", description = "Returns OK for service probes")
    @GetMapping("/")
    public ResponseEntity<String> root() {
        return ResponseEntity.ok("OK");
    }

    @Operation(summary = "Health check", description = "Returns OK if the service is running")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
