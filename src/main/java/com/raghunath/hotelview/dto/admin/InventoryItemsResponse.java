package com.raghunath.hotelview.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryItemsResponse {
    private String name;
    private String price;
    private String quantity;
    private String unit;
    private Boolean isPresent;
}