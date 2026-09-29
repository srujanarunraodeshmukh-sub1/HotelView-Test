package com.raghunath.hotelview.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CacheManager cacheManager;

    // 🌟 Roles that carry no hotelId and skip the staff single-session-kill check
    private static final Set<String> NON_STAFF_ROLES = Set.of("CUSTOMER", "DELIVERY_BOY");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();
        // 🌟 NARROWED: only the actual public login routes skip JWT parsing now —
        // refresh-token/logout for customer & delivery boy must be parsed and
        // authenticated like any other protected endpoint.
        return path.equals("/api/v1/user/auth/login") ||
                path.equals("/api/v1/delivery/auth/login") ||
                path.startsWith("/api/v1/info/") ||
                path.startsWith("/api/v1/admin/login") ||
                path.startsWith("/api/v1/admin/register") ||
                path.startsWith("/api/v1/employees/login") ||
                path.startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("No Bearer header on " + request.getRequestURI());
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();

            if (jwtUtil.validateToken(token)) {
                String role = jwtUtil.extractRole(token);

                // 🌟 CUSTOMER / DELIVERY_BOY: no hotelId claim, no session-kill check —
                // multiple simultaneous device logins are normal for these roles.
                if (role != null && NON_STAFF_ROLES.contains(role.toUpperCase())) {
                    String subjectId = jwtUtil.extractUserId(token);

                    List<GrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(subjectId, null, authorities);

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                } else {
                    String hotelId = jwtUtil.extractHotelId(token);

                    Cache activeSessionsCache = cacheManager.getCache("activeSessionsCache");
                    Boolean isSessionActive = null;

                    if (activeSessionsCache != null) {
                        isSessionActive = activeSessionsCache.get(hotelId, Boolean.class);
                    }

                    if (isSessionActive == null || isSessionActive) {
                        String userType = jwtUtil.extractUserType(token);

                        List<GrantedAuthority> authorities = new ArrayList<>();
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                        if (userType != null && !userType.isBlank()) {
                            authorities.add(new SimpleGrantedAuthority("USERTYPE_" + userType.trim().toUpperCase()));
                        }

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(hotelId, null, authorities);

                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\": \"SESSION_EXPIRED\", \"message\": \"Your session was terminated by another device.\"}");
                        return;
                    }
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}