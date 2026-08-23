package com.raghunath.hotelview.dto.restaurantandinstant;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MenuVersionDTO {
    private long totalItems;
    private long lastUpdatedTimestamp;
}