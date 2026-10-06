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
    private final Map<String, Bucket> customerLoginCache = new ConcurrentHashMap<>();
    private final Map<String, Bucket> nonStaffCache = new ConcurrentHashMap<>();

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

    // 🌟 NEW: dedicated, tighter bucket just for customer login — 10/min/IP
    private Bucket createCustomerLoginBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
                .build();
    }

    // 🌟 NEW: general post-login traffic for customer + delivery boy — 10/min/IP
    private Bucket createNonStaffBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
                .build();
    }

    // 🌟 CHANGED: no longer skips /api/v1/user/** — that traffic must pass
    // through so it can be matched against the buckets below.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getServletPath();
        String ip = request.getRemoteAddr();

        // 🛡️ SHIELD 0: CUSTOMER LOGIN — dedicated 10/min/IP, checked before the generic auth shield
        if (path.equals("/api/v1/user/auth/login")) {
            Bucket bucket = customerLoginCache.computeIfAbsent(ip, k -> createCustomerLoginBucket());
            if (!bucket.tryConsume(1)) {
                sendTooManyRequestsResponse(response, "Too many login attempts. Please try again after some time.");
                return;
            }
        }
        // 🛡️ SHIELD 1: PROTECT GATEWAYS (admin/employee/delivery-boy login, all refresh-token routes)
        else if (path.contains("/login") || path.contains("/register") || path.contains("/refresh-token")) {
            Bucket bucket = authCache.computeIfAbsent(ip, k -> createAuthBucket());
            if (!bucket.tryConsume(1)) {
                sendTooManyRequestsResponse(response, "Authentication threshold exceeded. Please try again after some time.");
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
        // 🛡️ SHIELD 3: CUSTOMER & DELIVERY BOY POST-LOGIN TRAFFIC — 10/min/IP
        else if ((path.startsWith("/api/v1/user/") && !path.startsWith("/api/v1/user/auth/")) ||
                (path.startsWith("/api/v1/delivery/") && !path.startsWith("/api/v1/delivery/auth/"))) {
            Bucket bucket = nonStaffCache.computeIfAbsent(ip, k -> createNonStaffBucket());
            if (!bucket.tryConsume(1)) {
                sendTooManyRequestsResponse(response, "Too many requests. Please slow down.");
                return;
            }
        }

        // MEMORY SAFETY GUARD
        if (authCache.size() > 10000) authCache.clear();
        if (publicCache.size() > 10000) publicCache.clear();
        if (customerLoginCache.size() > 10000) customerLoginCache.clear();
        if (nonStaffCache.size() > 10000) nonStaffCache.clear();

        filterChain.doFilter(request, response);
    }

    private void sendTooManyRequestsResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"status\": 429, \"error\": \"Too Many Requests\", \"message\": \"" + message + "\"}");
    }
}