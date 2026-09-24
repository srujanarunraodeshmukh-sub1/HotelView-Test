package com.raghunath.hotelview.dto.customer;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CustomerAuthResponse {
    private String customerId;
    private String accessToken;
    private String refreshToken;
}