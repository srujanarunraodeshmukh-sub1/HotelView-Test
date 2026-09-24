package com.raghunath.hotelview.controller.customer;

import com.raghunath.hotelview.dto.customer.CustomerAuthResponse;
import com.raghunath.hotelview.dto.customer.CustomerLoginRequest;
import com.raghunath.hotelview.dto.customer.CustomerRefreshTokenRequest;
import com.raghunath.hotelview.service.customer.CustomerAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/user/auth")
@RequiredArgsConstructor
public class CustomerAuthController {

    private final CustomerAuthService customerAuthService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody CustomerLoginRequest request) {
        CustomerAuthResponse tokens = customerAuthService.loginWithMobile(request.getMobileNumber(), request.getDeviceInfo());
        return ResponseEntity.ok(Map.of("success", true, "message", "Logged in successfully.", "data", tokens));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<Map<String, Object>> refresh(@Valid @RequestBody CustomerRefreshTokenRequest request) {
        CustomerAuthResponse tokens = customerAuthService.refreshTokens(request.getRefreshToken(), request.getDeviceInfo());
        return ResponseEntity.ok(Map.of("success", true, "data", tokens));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@Valid @RequestBody CustomerRefreshTokenRequest request) {
        customerAuthService.logout(request.getRefreshToken());
        return ResponseEntity.ok(Map.of("success", true, "message", "Logged out successfully."));
    }
}