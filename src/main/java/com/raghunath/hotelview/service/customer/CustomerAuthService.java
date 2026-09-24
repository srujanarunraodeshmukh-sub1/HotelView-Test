package com.raghunath.hotelview.service.customer;

import com.raghunath.hotelview.dto.customer.CustomerAuthResponse;
import com.raghunath.hotelview.entity.customer.CustomerRefreshToken;
import com.raghunath.hotelview.entity.customer.CustomerUser;
import com.raghunath.hotelview.repository.customer.CustomerRefreshTokenRepository;
import com.raghunath.hotelview.repository.customer.CustomerUserRepository;
import com.raghunath.hotelview.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class CustomerAuthService {

    private final CustomerUserRepository customerUserRepository;
    private final CustomerRefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    private static final long REFRESH_TOKEN_VALIDITY_MS = 1000L * 60 * 60 * 24 * 7; // 7 days — mirrors JwtUtil

    public CustomerAuthResponse loginWithMobile(String mobileNumber, String deviceInfo) {
        String normalizedMobile = normalizeMobile(mobileNumber);

        CustomerUser customer = customerUserRepository.findByMobileNumber(normalizedMobile)
                .orElseGet(() -> customerUserRepository.save(
                        CustomerUser.builder()
                                .mobileNumber(normalizedMobile)
                                .role("CUSTOMER")
                                .active(true)
                                .createdAt(LocalDateTime.now())
                                .build()
                ));

        if (!customer.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account has been disabled.");
        }

        customer.setLastLoginAt(LocalDateTime.now());
        customerUserRepository.save(customer);

        return issueTokens(customer.getId(), deviceInfo);
    }

    public CustomerAuthResponse refreshTokens(String refreshToken, String deviceInfo) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token.");
        }

        String tokenHash = hashToken(refreshToken);
        CustomerRefreshToken stored = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token not recognized or already used."));

        // single-use refresh tokens: revoke the old one the moment it's redeemed
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        String customerId = jwtUtil.extractUserId(refreshToken);
        CustomerUser customer = customerUserRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found."));

        if (!customer.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account has been disabled.");
        }

        return issueTokens(customer.getId(), deviceInfo);
    }

    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHashAndRevokedFalse(hashToken(refreshToken))
                .ifPresent(t -> {
                    t.setRevoked(true);
                    refreshTokenRepository.save(t);
                });
    }

    private CustomerAuthResponse issueTokens(String customerId, String deviceInfo) {
        String accessToken = jwtUtil.generateCustomerAccessToken(customerId);
        String refreshToken = jwtUtil.generateCustomerRefreshToken(customerId);

        refreshTokenRepository.save(CustomerRefreshToken.builder()
                .customerId(customerId)
                .tokenHash(hashToken(refreshToken))
                .deviceInfo(deviceInfo)
                .createdAt(LocalDateTime.now())
                .expiresAt(new Date(System.currentTimeMillis() + REFRESH_TOKEN_VALIDITY_MS))
                .revoked(false)
                .build());

        return new CustomerAuthResponse(customerId, accessToken, refreshToken);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String normalizeMobile(String mobile) {
        return mobile.trim().replaceAll("[^0-9+]", "");
    }
}