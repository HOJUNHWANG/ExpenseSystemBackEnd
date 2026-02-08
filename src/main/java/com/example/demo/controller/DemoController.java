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
        demoDataService.resetAndSeed();
        return ResponseEntity.ok("OK");
    }
}
