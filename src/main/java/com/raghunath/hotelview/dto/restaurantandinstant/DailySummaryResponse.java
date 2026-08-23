package com.raghunath.hotelview.dto.restaurantandinstant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DailySummaryResponse {

    @JsonProperty("_id")
    private String id;

    private String date;
    private String restaurantId;

    // Maps JSON field "totalCollection" to totalStaffCollection in Java
    @JsonProperty("totalCollection")
    private Double totalStaffCollection;

    @JsonProperty("totaltaxcollected")
    private Double totalTaxCollected;

    private Map<String, Double> gstBreakdown;

    // Maps JSON field "staff" to staffSummary in Java
    @JsonProperty("staff")
    private List<StaffSummaryDto> staffSummary;

    // Item Summary Metrics
    private Double totalQtySold;
    private List<ItemSummaryDto> itemSummary;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StaffSummaryDto {
        private String staffId;
        private String name;
        private Double amount;

        @JsonProperty("taxcollected")
        private Double taxCollected;

        private Map<String, Double> gstBreakdown;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ItemSummaryDto {
        private String itemId;
        private String name;
        private String unit;
        private Double qty;
        private Double totalAmount;
    }
}