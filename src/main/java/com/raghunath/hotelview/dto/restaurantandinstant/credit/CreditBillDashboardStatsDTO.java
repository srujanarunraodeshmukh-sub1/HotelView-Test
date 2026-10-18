package com.raghunath.hotelview.dto.restaurantandinstant.credit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditBillDashboardStatsDTO {

    private Long completedOrdersTodayCount;

    private Long homeDeliveriesCount;

    private Long instantCount;

    private Double last24HoursSalesRupees;

    private String planType;

    private String restaurantAddress;

    private String restaurantContact;

    private String restaurantLogo;

    private String restaurantName;

    private String restaurantUpi;

    private Double todaySalesRupees;
}