package com.raghunath.hotelview.dto.restaurantandinstant;

import lombok.Data;

@Data
public class RegisterRequest {
    private String name;
    private String mobile;
    private String password;
    private String confirmPassword;

    private String userType;

    private String privacyPolicy;              // Will receive: "accepted"
    private String privacyPolicyVersion;       // Will receive: "1.1.1"

    private String termsAndConditions;         // Will receive: "accepted"
    private String termsAndConditionsVersion;   // Will receive: "1.1.1"
}
