package com.raghunath.hotelview.dto.restaurantandinstant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyOrderGroupDTO {
    private String date;
    private Double totalAmount;
    private Integer totalOrder;
    private List<DeliverySummaryDTO> orders; // Holds the flat order list for this day
}