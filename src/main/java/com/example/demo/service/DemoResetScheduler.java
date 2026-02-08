package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DemoResetScheduler {

    private final DemoDataService demoDataService;

    @Value("${demo.reset.enabled:false}")
    private boolean enabled;

    // Default: midnight ET
    @Scheduled(cron = "${demo.reset.cron:0 0 0 * * *}", zone = "${demo.reset.zone:America/New_York}")
    public void nightlyReset() {
        if (!enabled) return;
        demoDataService.resetAndSeed();
    }
}
