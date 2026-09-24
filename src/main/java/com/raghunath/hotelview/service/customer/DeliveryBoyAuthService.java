package com.raghunath.hotelview.service.customer;


import com.raghunath.hotelview.dto.customer.DeliveryBoyAuthResponse;
import com.raghunath.hotelview.entity.customer.DeliveryBoyRefreshToken;
import com.raghunath.hotelview.entity.customer.DeliveryBoyUser;
import com.raghunath.hotelview.repository.customer.DeliveryBoyRefreshTokenRepository;
import com.raghunath.hotelview.repository.customer.DeliveryBoyUserRepository;
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
public class DeliveryBoyAuthService {

    private final DeliveryBoyUserRepository deliveryBoyUserRepository;
    private final DeliveryBoyRefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    private static final long REFRESH_TOKEN_VALIDITY_MS = 1000L * 60 * 60 * 24 * 7;

    public DeliveryBoyAuthResponse loginWithMobile(String mobileNumber, String deviceInfo) {
        String normalizedMobile = normalizeMobile(mobileNumber);

        DeliveryBoyUser deliveryBoy = deliveryBoyUserRepository.findByMobileNumber(normalizedMobile)
                .orElseGet(() -> deliveryBoyUserRepository.save(
                        DeliveryBoyUser.builder()
                                .mobileNumber(normalizedMobile)
                                .role("DELIVERY_BOY")
                                .active(true)
                                .createdAt(LocalDateTime.now())
                                .build()
                ));

        if (!deliveryBoy.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account has been disabled.");
        }

        deliveryBoy.setLastLoginAt(LocalDateTime.now());
        deliveryBoyUserRepository.save(deliveryBoy);

        return issueTokens(deliveryBoy.getId(), deviceInfo);
    }

    public DeliveryBoyAuthResponse refreshTokens(String refreshToken, String deviceInfo) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token.");
        }

        String tokenHash = hashToken(refreshToken);
        DeliveryBoyRefreshToken stored = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token not recognized or already used."));

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        String deliveryBoyId = jwtUtil.extractUserId(refreshToken);
        DeliveryBoyUser deliveryBoy = deliveryBoyUserRepository.findById(deliveryBoyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found."));

        if (!deliveryBoy.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account has been disabled.");
        }

        return issueTokens(deliveryBoy.getId(), deviceInfo);
    }

    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHashAndRevokedFalse(hashToken(refreshToken))
                .ifPresent(t -> {
                    t.setRevoked(true);
                    refreshTokenRepository.save(t);
                });
    }

    private DeliveryBoyAuthResponse issueTokens(String deliveryBoyId, String deviceInfo) {
        String accessToken = jwtUtil.generateDeliveryBoyAccessToken(deliveryBoyId);
        String refreshToken = jwtUtil.generateDeliveryBoyRefreshToken(deliveryBoyId);

        refreshTokenRepository.save(DeliveryBoyRefreshToken.builder()
                .deliveryBoyId(deliveryBoyId)
                .tokenHash(hashToken(refreshToken))
                .deviceInfo(deviceInfo)
                .createdAt(LocalDateTime.now())
                .expiresAt(new Date(System.currentTimeMillis() + REFRESH_TOKEN_VALIDITY_MS))
                .revoked(false)
                .build());

        return new DeliveryBoyAuthResponse(deliveryBoyId, accessToken, refreshToken);
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