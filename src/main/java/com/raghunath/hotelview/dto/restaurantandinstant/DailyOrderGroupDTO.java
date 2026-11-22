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
    private Double totalTransaction;
    private Double amountReceived;
    private Double amountPending;
    private Integer totalOrder;
    private List<DeliverySummaryDTO> orders; // Holds the flat order list for this day
}