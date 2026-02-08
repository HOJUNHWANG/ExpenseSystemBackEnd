package com.example.demo.config;

import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class WebConfig {

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOriginsRaw;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        final List<String> allowed = parseAllowedOrigins(allowedOriginsRaw);

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NonNull CorsRegistry registry) {
                var reg = registry.addMapping("/**")
                        .allowedMethods("*")
                        .allowedHeaders("*");

                if (allowed.size() == 1 && "*".equals(allowed.get(0))) {
                    // Public demo: allow any origin. Tighten this in production using APP_CORS_ALLOWED_ORIGINS.
                    reg.allowedOriginPatterns("*");
                } else {
                    reg.allowedOrigins(allowed.toArray(new String[0]));
                }
            }
        };
    }

    private static List<String> parseAllowedOrigins(String raw) {
        if (raw == null || raw.isBlank()) return List.of("*");
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
