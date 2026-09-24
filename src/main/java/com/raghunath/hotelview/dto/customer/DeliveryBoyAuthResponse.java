package com.raghunath.hotelview.dto.customer;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeliveryBoyAuthResponse {
    private String deliveryBoyId;
    private String accessToken;
    private String refreshToken;
}