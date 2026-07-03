package com.raghunath.hotelview.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryItemsResponse {
    private String name;
    private String price;     // Mapped to match String format in image_ce1a11.png
    private String quantity;  // e.g., "1 Liter"
    private String unit;      // e.g., "LITER"
}