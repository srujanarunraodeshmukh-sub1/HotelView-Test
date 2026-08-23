package com.raghunath.hotelview.dto.restaurantandinstant;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryItemsResponse {
    private String name;
    private String price;
    private String quantity;
    private String unit;
    private Double gstPercent;
    private Boolean isPresent;
}