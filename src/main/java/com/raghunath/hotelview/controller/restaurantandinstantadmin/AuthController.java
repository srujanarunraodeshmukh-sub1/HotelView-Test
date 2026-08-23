package com.raghunath.hotelview.controller.restaurantandinstantadmin;

import com.raghunath.hotelview.dto.restaurantandinstant.*;
import com.raghunath.hotelview.entity.restaurantandinstant.Admin;
import com.raghunath.hotelview.entity.restaurantandinstant.CustomerDetails;
import com.raghunath.hotelview.repository.restaurantandinstant.AdminRepository;
import com.raghunath.hotelview.security.UserTypeAccessGuard;
import com.raghunath.hotelview.service.restaurantandinstantadmin.AdminAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AuthController {

    private final AdminAuthService adminAuthService;
    private final AdminRepository adminRepository;
    private final UserTypeAccessGuard userTypeAccessGuard;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return adminAuthService.login(request);
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(adminAuthService.register(request));
    }

    @GetMapping("/profile")  // Allow Restaurant, Instant, Car
    public ResponseEntity<AdminProfileDTO> getProfile() {
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT", "BUYANDSELL");
        return ResponseEntity.ok(adminAuthService.getProfile(hotelId));
    }

    @PutMapping("/profile")  // Allow Restaurant, Instant, Car
    public ResponseEntity<String> updateProfile(@RequestBody AdminProfileDTO updates) {
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT", "BUYANDSELL");
        return ResponseEntity.ok(adminAuthService.updateProfile(hotelId, updates));
    }

    @GetMapping("/business")  // Allow Restaurant, Instant
    public ResponseEntity<BusinessDetailsDTO> getBusinessDetails() {
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");
        return ResponseEntity.ok(adminAuthService.getBusinessDetails(hotelId));
    }

    @PutMapping("/business")  // Allow Restaurant, Instant
    public ResponseEntity<BusinessDetailsDTO> updateBusinessDetails(@RequestBody BusinessDetailsDTO updates) {
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");
        return ResponseEntity.ok(adminAuthService.updateBusinessDetails(hotelId, updates));
    }

    @PostMapping("/submit/paymentDetails")  // Allow Restaurant, Instant
    public ResponseEntity<Map<String, String>> handlePaymentSubmission(
            @RequestBody Map<String, String> requestBody, // Changed to RequestBody
            Authentication authentication) {

        // 1. Extract values from JSON body
        String url = requestBody.get("url");
        String name = requestBody.get("name");
        String address = requestBody.get("address");

        // 2. Extract hotelId from Token
        String hotelId = authentication.getName();
        userTypeAccessGuard.enforceUserType(hotelId, "RESTAURANT", "INSTANT");

        // 3. Call Service logic
        String message = adminAuthService.submitPaymentProof(url, name, address, hotelId);

        Map<String, String> response = new HashMap<>();
        response.put("message", message);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/payment/status")  // Allow Restaurant, Instant
    public ResponseEntity<?> getPaymentStatus(Authentication authentication) {
        String hotelId = authentication.getName();
        userTypeAccessGuard.enforceUserType(hotelId, "RESTAURANT", "INSTANT");
        return ResponseEntity.ok(adminAuthService.getPaymentStatus(hotelId));
    }

    @GetMapping("/greeting")  // Allow Restaurant, Instant
    public ResponseEntity<Map<String, String>> getGreeting(Authentication authentication) {
        String hotelId = authentication.getName();
        userTypeAccessGuard.enforceUserType(hotelId, "RESTAURANT", "INSTANT");
        String message = adminAuthService.getGreetingForAdmin(hotelId);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PutMapping("/greeting")  // Allow Restaurant, Instant
    public ResponseEntity<Map<String, String>> setPersonalGreeting(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        String hotelId = authentication.getName();
        userTypeAccessGuard.enforceUserType(hotelId, "RESTAURANT", "INSTANT");

        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        admin.setGreeting(body.get("message"));
        adminRepository.save(admin);

        return ResponseEntity.ok(Map.of("status", "Greeting saved successfully."));
    }

    @DeleteMapping("/greeting")  // Allow Restaurant, Instant
    public ResponseEntity<Map<String, String>> deletePersonalGreeting(Authentication authentication) {
        String hotelId = authentication.getName();
        userTypeAccessGuard.enforceUserType(hotelId, "RESTAURANT", "INSTANT");
        String result = adminAuthService.deletePersonalGreeting(hotelId);
        return ResponseEntity.ok(Map.of("status", result));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<Map<String, Object>> refresh(@RequestBody Map<String, String> request) {
        String oldRefreshToken = request.get("refreshToken");

        if (oldRefreshToken == null || oldRefreshToken.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Refresh token is required"));
        }

        Map<String, Object> tokens = adminAuthService.refreshAdminToken(oldRefreshToken);

        return ResponseEntity.ok(tokens);
    }

    @PutMapping("/connect-platform")  // Allow Restaurant, Instant
    public ResponseEntity<String> connectPlatform(@RequestBody PlatformConfigRequest request) {
        // Extract hotelId from the current authenticated user (JWT)
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT"); // This usually holds your hotelId/username

        adminAuthService.updatePlatformId(hotelId, request);

        return ResponseEntity.ok(request.getPlatformName() + " connected successfully!");
    }

    @GetMapping("/integrations")  // Allow Restaurant, Instant
    public ResponseEntity<Map<String, String>> getIntegrations() {
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        return ResponseEntity.ok(admin.getPlatformIds());
    }

    @GetMapping("/integration-config/{platformName}")  // Allow Restaurant, Instant
    public ResponseEntity<Map<String, String>> getIntegrationConfig(@PathVariable String platformName) {
        // 1. Get the current Admin
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");

        // 2. Prepare the data for the Frontend
        Map<String, String> config = new HashMap<>();
        config.put("webhookUrl", "https://hotelview-api.onrender.com/api/v1/orders/webhook");
        config.put("platform", platformName.toUpperCase());

        // 3. (Optional) Provide a Secret Token for security
        config.put("webhookSecret", "hv_secret_" + hotelId.hashCode());

        return ResponseEntity.ok(config);
    }

    @GetMapping("/plan/details")  // Allow Restaurant, Instant
    public ResponseEntity<Map<String, Object>> getSubscriptionInfo() {
        String hotelId = userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT", "INSTANT");
        return ResponseEntity.ok(adminAuthService.getSubscriptionDashboard(hotelId));
    }

    @GetMapping("/customer/details")  // Allow Restaurant, Instant
    public ResponseEntity<PagedModel<CustomerDetails>> getAllCustomers(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page) {

        String hotelId = authentication.getName();
        userTypeAccessGuard.enforceUserType(hotelId, "RESTAURANT", "INSTANT");
        Page<CustomerDetails> result = adminAuthService.getCustomersByHotel(hotelId, page);
        return ResponseEntity.ok(new PagedModel<>(result));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestBody Map<String, String> request) {
        // Get the token from the body
        String refreshToken = request.get("refreshToken");

        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.badRequest().body("Refresh token required for logout");
        }

        // Pass the TOKEN, not the ID
        adminAuthService.logoutAdmin(refreshToken);

        return ResponseEntity.ok("Admin logged out successfully");
    }
}