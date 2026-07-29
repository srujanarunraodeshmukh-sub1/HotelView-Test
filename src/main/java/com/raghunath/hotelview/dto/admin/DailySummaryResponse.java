package com.raghunath.hotelview.dto.admin;

import lombok.*;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DailySummaryResponse {
    private String date;
    private String restaurantId;

    // Staff Summary Metrics
    private Double totalStaffCollection;
    private List<StaffSummaryDto> staffSummary;

    // Item Summary Metrics
    private Double totalQtySold;
    private List<ItemSummaryDto> itemSummary;

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class StaffSummaryDto {
        private String staffId;
        private String name;
        private Double amount;
    }

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
    public static class ItemSummaryDto {
        private String itemId;
        private String name;
        private String unit;
        private Double qty;
    }
}