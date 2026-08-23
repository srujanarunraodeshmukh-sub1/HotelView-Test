package com.raghunath.hotelview.dto.restaurantandinstant;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AcceptCustomerOrderRequest {

    @NotBlank(message = "Customer Order ID is required")
    private String customerOrderId;
}