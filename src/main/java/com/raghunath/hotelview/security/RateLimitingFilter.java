package com.raghunath.hotelview.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter implements Filter {

    // Thread-safe map storing a token bucket for each individual client IP
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    // Defines standard request limits (e.g., maximum 20 public hits per minute, refills 20 tokens every minute)
    // Keep the classic bandwidth small and tight for a Hobby setup
    private Bucket createNewBucket() {
        return Bucket.builder()
                // 10 requests max per window, refills completely every minute
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
                .build();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();

        // 🌟 Target only vulnerable open public paths to save CPU performance cycles on authenticated APIs
        if (path.startsWith("/api/v1/admin/login") ||
                path.startsWith("/api/v1/admin/register") ||
                path.startsWith("/api/v1/employees/login") ||
                path.startsWith("/api/v1/info/")) {

            String ip = httpRequest.getRemoteAddr();
            Bucket bucket = cache.computeIfAbsent(ip, k -> createNewBucket());

            // Try to consume 1 token from the IP's bucket
            if (!bucket.tryConsume(1)) {
                httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"error\": \"Too many requests. Please slow down and try again later.\"}");
                return; // Block execution thread right here!
            }
        }

        chain.doFilter(request, response);
    }
}