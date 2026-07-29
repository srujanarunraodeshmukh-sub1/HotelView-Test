package com.raghunath.hotelview.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitingFilter implements Filter {

    // Separate thread-safe tracking maps to keep performance high
    private final Map<String, Bucket> authCache = new ConcurrentHashMap<>();
    private final Map<String, Bucket> publicCache = new ConcurrentHashMap<>();

    // 🔒 Strict Limit for Critical Auth Endpoints (Login/Register/Token Refresh)
    private Bucket createAuthBucket() {
        return Bucket.builder()
                // Allows a small burst of 15 attempts, refilling 15 tokens every minute to shield against brute force
                .addLimit(Bandwidth.classic(15, Refill.intervally(15, Duration.ofMinutes(1))))
                .build();
    }

    // 🌍 Relaxed Limit for General Public Endpoints (/info/**, public syncs, webhooks)
    private Bucket createPublicBucket() {
        return Bucket.builder()
                // Allows 100 requests per minute per IP to handle swift multi-asset UI rendering easily
                .addLimit(Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1))))
                .build();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();
        String ip = httpRequest.getRemoteAddr();

        // 🛡️ SHIELD 1: PROTECT GATEWAYS (Login, Register, Refresh)
        if (path.contains("/login") || path.contains("/register") || path.contains("/refresh-token")) {
            Bucket bucket = authCache.computeIfAbsent(ip, k -> createAuthBucket());
            if (!bucket.tryConsume(1)) {
                sendTooManyRequestsResponse(httpResponse, "Authentication threshold exceeded. Please try again in a minute.");
                return;
            }
        }
        // 🛡️ SHIELD 2: GENERAL PUBLIC DATA ACCESS
        else if (path.startsWith("/api/v1/info/") || path.startsWith("/api/v1/sync/")) {
            Bucket bucket = publicCache.computeIfAbsent(ip, k -> createPublicBucket());
            if (!bucket.tryConsume(1)) {
                sendTooManyRequestsResponse(httpResponse, "Too many requests. Please slow down.");
                return;
            }
        }

        // 🧼 MEMORY SAFETY GUARD: Prevents continuous accumulation of tracking data from crashing your RAM
        if (authCache.size() > 10000) {
            authCache.clear();
        }
        if (publicCache.size() > 10000) {
            publicCache.clear();
        }

        chain.doFilter(request, response);
    }

    private void sendTooManyRequestsResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"status\": 429, \"error\": \"Too Many Requests\", \"message\": \"" + message + "\"}");
    }
}