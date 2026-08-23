package com.raghunath.hotelview.dto.restaurantandinstant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptGstToggleDTO {
    private String gstType;
    private Double gstPercent;
    private boolean enabled;
}
