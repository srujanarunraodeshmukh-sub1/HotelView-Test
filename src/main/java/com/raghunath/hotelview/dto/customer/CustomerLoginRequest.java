package com.raghunath.hotelview.dto.customer;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CustomerLoginRequest {

    @NotBlank(message = "Mobile number is required")
    private String mobileNumber;

    private String deviceInfo; // optional — app name/model, for session tracking
}