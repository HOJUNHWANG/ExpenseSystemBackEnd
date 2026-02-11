package com.example.demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-memory rate limiting for the public demo.
 *
 * Goals:
 * - Prevent obvious abuse (reset spamming, high-frequency writes)
 * - Keep the demo usable without accounts/keys
 *
 * Notes:
 * - In-memory per-instance only (sufficient for small public demo)
 * - Keep limits lenient enough to not break normal demo usage
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${demo.guard.enabled:true}")
    private boolean enabled;

    // Demo reset endpoint is the most sensitive.
    @Value("${demo.guard.reset.max-per-minute:6}")
    private int resetMaxPerMinute;

    @Value("${demo.guard.reset.cooldown-seconds:20}")
    private int resetCooldownSeconds;

    // Generic write endpoints (create/update/submit/approve/reject/decide)
    @Value("${demo.guard.writes.max-per-minute:120}")
    private int writesMaxPerMinute;

    private final Map<String, TokenBucket> resetBuckets = new ConcurrentHashMap<>();
    private final Map<String, TokenBucket> writeBuckets = new ConcurrentHashMap<>();
    private final Map<String, Long> resetLastMs = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) return true;

        String method = request.getMethod();
        if (!"POST".equalsIgnoreCase(method) && !"PUT".equalsIgnoreCase(method) && !"DELETE".equalsIgnoreCase(method)) {
            return true;
        }

        String path = request.getRequestURI();
        // Only guard our API.
        return path == null || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String ip = clientIp(request);
        String path = request.getRequestURI();

        // 1) Reset cooldown + RPM
        if ("/api/demo/reset".equals(path)) {
            long now = System.currentTimeMillis();
            Long last = resetLastMs.get(ip);
            if (last != null && (now - last) < (resetCooldownSeconds * 1000L)) {
                tooMany(response, "Reset cooldown active. Please wait a bit and try again.");
                return;
            }

            TokenBucket bucket = resetBuckets.computeIfAbsent(ip, k -> TokenBucket.perMinute(resetMaxPerMinute));
            if (!bucket.tryConsume()) {
                tooMany(response, "Too many reset requests. Please slow down.");
                return;
            }

            resetLastMs.put(ip, now);
            filterChain.doFilter(request, response);
            return;
        }

        // 2) Generic write rate limiting for the rest of POST/PUT/DELETE API writes.
        if (isWriteEndpoint(path)) {
            TokenBucket bucket = writeBuckets.computeIfAbsent(ip, k -> TokenBucket.perMinute(writesMaxPerMinute));
            if (!bucket.tryConsume()) {
                tooMany(response, "Too many requests. Please slow down.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isWriteEndpoint(String path) {
        if (path == null) return false;
        // Keep it broad but API-only.
        if (path.startsWith("/api/expense-reports")) return true;
        if (path.startsWith("/api/auth/")) return true;
        if (path.startsWith("/api/demo/")) return true;
        return false;
    }

    private static void tooMany(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + escapeJson(msg) + "\"}");
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String clientIp(HttpServletRequest request) {
        // Render sits behind proxies; X-Forwarded-For is typically present.
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // Take the first IP in the list.
            String first = xff.split(",")[0].trim();
            if (!first.isBlank()) return first;
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    static final class TokenBucket {
        private final int capacity;
        private final long refillEveryMs;

        private int tokens;
        private long lastRefillMs;

        private TokenBucket(int capacity, long refillEveryMs) {
            this.capacity = Math.max(1, capacity);
            this.refillEveryMs = Math.max(1000L, refillEveryMs);
            this.tokens = this.capacity;
            this.lastRefillMs = System.currentTimeMillis();
        }

        static TokenBucket perMinute(int maxPerMinute) {
            int cap = Math.max(1, maxPerMinute);
            // refill 1 token at a time over the minute.
            long every = Duration.ofMinutes(1).toMillis() / cap;
            return new TokenBucket(cap, every);
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens <= 0) return false;
            tokens -= 1;
            return true;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillMs;
            if (elapsed < refillEveryMs) return;

            int add = (int) (elapsed / refillEveryMs);
            if (add <= 0) return;

            tokens = Math.min(capacity, tokens + add);
            lastRefillMs = now;
        }
    }
}
