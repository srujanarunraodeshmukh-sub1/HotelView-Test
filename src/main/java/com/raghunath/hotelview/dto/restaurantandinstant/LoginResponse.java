package com.raghunath.hotelview.dto.restaurantandinstant;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class LoginResponse {
    private String message;
    private String adminId;
    private String hotelId;
    private String accessToken;
    private String refreshToken;

    // Profile Details
    private String name;
    private String mobile;
    private String alternateMobile;
    private List<String> email;
    private String restaurantUpi;
    private String restaurantName;
    private String address;
    private boolean isPlanExpired;
    private String expiryDate;
    private String userType;
}