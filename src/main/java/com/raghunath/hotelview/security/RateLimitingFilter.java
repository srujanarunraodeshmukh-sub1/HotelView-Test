package com.raghunath.hotelview.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> authCache = new ConcurrentHashMap<>();
    private final Map<String, Bucket> publicCache = new ConcurrentHashMap<>();

    private Bucket createAuthBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(15, Refill.intervally(15, Duration.ofMinutes(1))))
                .build();
    }

    private Bucket createPublicBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1))))
                .build();
    }

    // 🌟 CRITICAL FIX: Explicitly exclude public customer QR routes from being blocked
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();
        return path.startsWith("/api/v1/user/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getServletPath();
        String ip = request.getRemoteAddr();

        // 🛡️ SHIELD 1: PROTECT GATEWAYS
        if (path.contains("/login") || path.contains("/register") || path.contains("/refresh-token")) {
            Bucket bucket = authCache.computeIfAbsent(ip, k -> createAuthBucket());
            if (!bucket.tryConsume(1)) {
                sendTooManyRequestsResponse(response, "Authentication threshold exceeded. Please try again in a minute.");
                return;
            }
        }
        // 🛡️ SHIELD 2: GENERAL PUBLIC DATA ACCESS
        else if (path.startsWith("/api/v1/info/") || path.startsWith("/api/v1/sync/")) {
            Bucket bucket = publicCache.computeIfAbsent(ip, k -> createPublicBucket());
            if (!bucket.tryConsume(1)) {
                sendTooManyRequestsResponse(response, "Too many requests. Please slow down.");
                return;
            }
        }

        // MEMORY SAFETY GUARD
        if (authCache.size() > 10000) authCache.clear();
        if (publicCache.size() > 10000) publicCache.clear();

        filterChain.doFilter(request, response);
    }

    private void sendTooManyRequestsResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"status\": 429, \"error\": \"Too Many Requests\", \"message\": \"" + message + "\"}");
    }
}