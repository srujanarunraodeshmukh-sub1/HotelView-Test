package com.raghunath.hotelview.config;

import com.raghunath.hotelview.security.JwtAuthenticationFilter;
import com.raghunath.hotelview.security.RateLimitingFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("JWT authentication is used");
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * 🌟 NEW: builds an AuthorizationManager that requires the caller to hold
     * AT LEAST ONE of the given roles AND AT LEAST ONE of the given userTypes.
     * Reads both straight off the authorities granted by JwtAuthenticationFilter
     * (ROLE_x and USERTYPE_x) — no DB lookup, no dependency on which Admin
     * entity/repository handled login. This is the single choke point: a
     * BuyAndSell (or any non-matching) token cannot pass a path guarded this
     * way, regardless of what any individual controller does or doesn't check.
     */
    private AuthorizationManager<RequestAuthorizationContext> requireRoleAndUserType(
            Set<String> allowedRoles, Set<String> allowedUserTypes) {

        return (Supplier<Authentication> authenticationSupplier, RequestAuthorizationContext context) -> {
            Authentication authentication = authenticationSupplier.get();

            if (authentication == null || !authentication.isAuthenticated()) {
                return new AuthorizationDecision(false);
            }

            boolean roleOk = authentication.getAuthorities().stream()
                    .anyMatch(a -> allowedRoles.contains(a.getAuthority().replace("ROLE_", "")));

            boolean userTypeOk = authentication.getAuthorities().stream()
                    .anyMatch(a -> allowedUserTypes.contains(a.getAuthority().replace("USERTYPE_", "")));

            return new AuthorizationDecision(roleOk && userTypeOk);
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // 🌟 Reusable guards for the two account-type groups in play.
        AuthorizationManager<RequestAuthorizationContext> restaurantOrInstantAdminOrWaiter =
                requireRoleAndUserType(Set.of("ADMIN", "WAITER"), Set.of("RESTAURANT", "INSTANT"));

        AuthorizationManager<RequestAuthorizationContext> restaurantOnlyAdminOrWaiter =
                requireRoleAndUserType(Set.of("ADMIN", "WAITER"), Set.of("RESTAURANT"));

        AuthorizationManager<RequestAuthorizationContext> restaurantOnlyWaiterChefAdmin =
                requireRoleAndUserType(Set.of("WAITER", "CHEF", "ADMIN"), Set.of("RESTAURANT"));

        AuthorizationManager<RequestAuthorizationContext> restaurantOnlyChefAdmin =
                requireRoleAndUserType(Set.of("CHEF", "ADMIN"), Set.of("RESTAURANT"));

        AuthorizationManager<RequestAuthorizationContext> restaurantOnlyAdminWaiter =
                requireRoleAndUserType(Set.of("ADMIN", "WAITER"), Set.of("RESTAURANT"));

        AuthorizationManager<RequestAuthorizationContext> restaurantInstantAdminOnly =
                requireRoleAndUserType(Set.of("ADMIN"), Set.of("RESTAURANT", "INSTANT"));

        AuthorizationManager<RequestAuthorizationContext> restaurantOnlyAdminOnly =
                requireRoleAndUserType(Set.of("ADMIN"), Set.of("RESTAURANT"));

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // 1. PUBLIC ENDPOINTS (No Token Needed)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/user/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(
                                "/api/v1/admin/login",
                                "/api/v1/admin/register",
                                "/api/v1/employees/login",
                                "/api/v1/sync/test-bump",
                                "/api/v1/admin/refresh-token",
                                "/api/v1/employees/refresh-token",
                                "/api/v1/info/terms",
                                "/api/v1/info/privacy",
                                "/api/v1/info/**",
                                "/api/v1/orders/webhook",
                                "/ws-node/**"
                        ).permitAll()

                        .requestMatchers("/actuator/**").hasRole("ADMIN")

                        .requestMatchers("/api/v1/admin/logout", "/api/v1/employees/logout").authenticated()

                        // 2. STAFF & USER MANAGEMENT (Admin Only)
                        // 🌟 These are restaurant/instant staff-management concepts — a
                        // BuyAndSell or Car account has no "employees" or "tables", so
                        // gate by userType here too, not just role.
                        .requestMatchers(
                                "/api/v1/employees/register",
                                "/api/v1/admin/profile",
                                "/api/v1/employees/list",
                                "/api/v1/employees/**",
                                "/api/v1/admin/myplan/details",
                                "/api/v1/admin/plan/details",
                                "/api/v1/admin/integrations",
                                "/api/v1/admin/customer/details",
                                "/api/v1/admin/submit/paymentDetails"
                        ).access(restaurantInstantAdminOnly)

                        // 3. TABLE OPERATIONS — Restaurant only (Instant has no tables)
                        .requestMatchers(HttpMethod.GET, "/api/v1/tables").access(restaurantOnlyAdminWaiter)
                        .requestMatchers("/api/v1/tables/transfer/**").access(restaurantOnlyAdminWaiter)
                        .requestMatchers("/api/v1/tables/**").access(restaurantOnlyAdminOnly)

                        // 4. KITCHEN OPERATIONS — Restaurant only
                        .requestMatchers(HttpMethod.PUT, "/api/v1/orders/kitchen/*/confirm-edit").access(restaurantOnlyAdminOrWaiter)
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/kitchen/**").access(restaurantOnlyWaiterChefAdmin)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/orders/kitchen/**").access(restaurantOnlyChefAdmin)

                        // 5. ORDER & SALES OPERATIONS — Restaurant + Instant only
                        // 🌟 THIS is the block that closes the gap we were chasing: checkout,
                        // direct-checkout, instant/checkout, completed orders, dashboard stats,
                        // etc. now require USERTYPE_RESTAURANT or USERTYPE_INSTANT in addition
                        // to the role — a BuyAndSell token fails here regardless of which
                        // Admin entity/repository its login went through.
                        .requestMatchers("/api/v1/orders/completed/*/inventory-profiles").access(restaurantOnlyAdminOnly)
                        .requestMatchers("/api/v1/orders/summary/completed/**").access(restaurantOnlyAdminOnly)
                        .requestMatchers(
                                "/api/v1/orders/draft/**",
                                "/api/v1/orders/confirm/**",
                                "/api/v1/orders/table/**",
                                "/api/v1/admin/business",
                                "/api/v1/orders/external/accept/",
                                "/api/v1/orders/checkout",
                                "/api/v1/orders/direct-checkout",
                                "/api/v1/orders/instant/checkout",
                                "/api/v1/orders/instant/direct-checkout",
                                "/api/v1/orders/completed/transfer/**",
                                "/api/v1/orders/completed/delivery/today",
                                "/api/v1/orders/completed/**",
                                "/api/v1/orders/dashboard/stats"
                        ).access(restaurantOrInstantAdminOrWaiter)

                        // 5b. Receipt settings — Restaurant + Instant
                        .requestMatchers(HttpMethod.GET, "/api/v1/receipt-settings/**").access(restaurantOrInstantAdminOrWaiter)
                        .requestMatchers(HttpMethod.POST, "/api/v1/receipt-settings/**").access(restaurantInstantAdminOnly)

                        // 6. MENU & SYNC OPERATIONS — Restaurant + Instant only
                        // 🌟 A BuyAndSell/Car account has no menu concept at all.
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/menu/**").access(restaurantInstantAdminOnly)
                        .requestMatchers("/api/v1/menu/version").access(requireRoleAndUserType(Set.of("ADMIN", "WAITER", "CHEF"), Set.of("RESTAURANT", "INSTANT")))
                        .requestMatchers(HttpMethod.POST, "/api/v1/menu/**").access(restaurantInstantAdminOnly)
                        .requestMatchers(HttpMethod.GET, "/api/v1/menu/**").access(restaurantOrInstantAdminOrWaiter)

                        // 7. BUY & SELL — its own account type, nothing to do with restaurant/instant
                        .requestMatchers("/api/v1/items/**").access(requireRoleAndUserType(Set.of("ADMIN"), Set.of("BUYANDSELL")))

                        // 8. ACCOUNT OPERATIONS
                        .requestMatchers("/api/v1/admin/logout", "/api/v1/employees/logout").authenticated()

                        // 9. CATCH-ALL
                        .anyRequest().authenticated()
                )

                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthenticationFilter, RateLimitingFilter.class)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:3000",
                "http://localhost:5173",
                "https://hotelview-tau.vercel.app",
                "http://localhost",
                "https://localhost",
                "https://netbill.madhavaglobal.in",
                "capacitor://localhost",
                "https://www.madhavaglobal.in",
                "https://hotel-view-scanner.vercel.app"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}