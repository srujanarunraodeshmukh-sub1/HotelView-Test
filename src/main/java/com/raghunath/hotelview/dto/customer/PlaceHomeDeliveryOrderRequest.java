package com.raghunath.hotelview.dto.customer;

import com.raghunath.hotelview.dto.restaurantandinstant.OrderItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class PlaceHomeDeliveryOrderRequest {

    @NotBlank(message = "Hotel ID is required")
    private String hotelId;

    @NotBlank(message = "Customer name is required")
    private String customerName;

    @NotBlank(message = "Customer mobile is required")
    private String customerMobile;

    private String customerAddress;

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItem> items;

    private Double totalAmount;

    private String comments;
}