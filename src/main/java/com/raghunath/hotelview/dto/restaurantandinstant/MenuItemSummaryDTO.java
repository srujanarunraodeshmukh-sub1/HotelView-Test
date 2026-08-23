package com.raghunath.hotelview.dto.restaurantandinstant;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemSummaryDTO {
    private String id;
    private String category;
    private String name;
    private String shortCode;
    private String description;
    private BigDecimal price;
    private String quantity;
    private Double totalQuantity;
    private Double lastAddedStock;
    private String totalquantityUnit;
    private String unit;
    private Boolean isVeg;
    private Boolean isAvailable;
    private String imageUrl;
    private Double gstPercent;
}