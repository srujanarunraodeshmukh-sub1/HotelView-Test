package com.raghunath.hotelview.dto.restaurantandinstant;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank
    private String mobile;

    @NotBlank
    private String password;

    @NotBlank
    private String userType;

}