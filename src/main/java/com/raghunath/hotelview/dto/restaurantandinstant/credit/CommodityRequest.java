package com.raghunath.hotelview.dto.restaurantandinstant.credit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommodityRequest {

    private String id;

    @NotBlank(message = "Commodity name is required")
    private String name;

    @NotNull(message = "Rate is required")
    @PositiveOrZero(message = "Rate cannot be negative")
    private Double rate;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than zero")
    private Double quantity;

    @NotBlank(message = "Unit is required")
    private String unit;
}