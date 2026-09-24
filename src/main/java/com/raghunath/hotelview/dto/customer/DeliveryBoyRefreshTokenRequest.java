package com.raghunath.hotelview.dto.customer;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeliveryBoyRefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    private String deviceInfo;
}