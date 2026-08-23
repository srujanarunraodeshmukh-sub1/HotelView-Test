package com.raghunath.hotelview.dto.restaurantandinstant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CustomerOrderRequest {

    @NotBlank(message = "Hotel ID is required")
    private String hotelId;

    @NotBlank(message = "Table ID or Name is required")
    private String tableId;

    @Valid
    @NotEmpty(message = "At least one order item is required")
    private List<OrderItem> items;

    private String comment;
}