package com.raghunath.hotelview.controller.customer;

import com.raghunath.hotelview.dto.customer.DeliveryBoyAuthResponse;
import com.raghunath.hotelview.dto.customer.DeliveryBoyLoginRequest;
import com.raghunath.hotelview.dto.customer.DeliveryBoyRefreshTokenRequest;
import com.raghunath.hotelview.service.customer.DeliveryBoyAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/delivery/auth")
@RequiredArgsConstructor
public class DeliveryBoyAuthController {

    private final DeliveryBoyAuthService deliveryBoyAuthService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody DeliveryBoyLoginRequest request) {
        DeliveryBoyAuthResponse tokens = deliveryBoyAuthService.loginWithMobile(request.getMobileNumber(), request.getDeviceInfo());
        return ResponseEntity.ok(Map.of("success", true, "message", "Logged in successfully.", "data", tokens));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<Map<String, Object>> refresh(@Valid @RequestBody DeliveryBoyRefreshTokenRequest request) {
        DeliveryBoyAuthResponse tokens = deliveryBoyAuthService.refreshTokens(request.getRefreshToken(), request.getDeviceInfo());
        return ResponseEntity.ok(Map.of("success", true, "data", tokens));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@Valid @RequestBody DeliveryBoyRefreshTokenRequest request) {
        deliveryBoyAuthService.logout(request.getRefreshToken());
        return ResponseEntity.ok(Map.of("success", true, "message", "Logged out successfully."));
    }
}