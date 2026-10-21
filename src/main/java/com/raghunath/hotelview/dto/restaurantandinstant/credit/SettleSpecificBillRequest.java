package com.raghunath.hotelview.dto.restaurantandinstant.credit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class SettleSpecificBillRequest {

    @NotNull(message = "Payment amount is required")
    @Positive(message = "Payment amount must be greater than zero")
    private Double amountReceived;
}